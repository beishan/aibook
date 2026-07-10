import Foundation
import UIKit
import Observation

// MARK: - ReaderViewModel（与安卓 ReaderViewModel 对齐 — 完整实现含远程阅读+进度同步）

@Observable
final class ReaderViewModel {
    var chapters: [ReaderChapter] = []
    var currentChapterIndex: Int = 0
    var isLoading: Bool = true
    var showControls: Bool = false
    var showContents: Bool = false
    var showSettings: Bool = false
    var showThemeSheet: Bool = false
    var showBookmarkSheet: Bool = false

    var settings: ReaderSettings = ReaderSettings()
    var snapshotSettings: ReaderSettings?

    // 亮度控制
    var brightness: CGFloat = UIScreen.main.brightness
    let brightnessControl = ReaderBrightnessControl()

    // 书签
    var bookmarks: [ReaderBookmark] = []

    // 阅读计时
    private var readingStartTime: Date?
    private var totalReadingSeconds: Int = 0

    private var bookId: String = ""
    private var remoteBookId: Int64?
    private var isRemote: Bool = false
    private let locator: ServiceLocator

    init(locator: ServiceLocator = .shared) {
        self.locator = locator
    }

    // MARK: - 计算属性

    var currentChapter: ReaderChapter? {
        chapters[safe: currentChapterIndex]
    }

    var progressPercent: Double {
        guard !chapters.isEmpty else { return 0 }
        return Double(currentChapterIndex) / Double(max(chapters.count - 1, 1))
    }

    var positionLabel: String {
        "第 \(currentChapterIndex + 1)/\(chapters.count) 章"
    }

    var isCurrentChapterBookmarked: Bool {
        guard let chapter = currentChapter else { return false }
        return bookmarks.contains { $0.chapterHref == chapter.href }
    }

    // MARK: - 加载

    func loadBook(bookId: String, isRemote: Bool) {
        self.bookId = bookId
        self.isRemote = isRemote
        self.settings = locator.readerSettingsStore.snapshot()
        self.snapshotSettings = settings
        self.brightness = brightnessControl.getBrightness()
        self.readingStartTime = Date()

        loadBookmarks()
        applyScreenAlwaysOn()

        Task {
            if isRemote {
                await loadRemoteBook(bookId: bookId)
            } else {
                await loadLocalBook(bookId: bookId)
            }
            await MainActor.run {
                isLoading = false
            }
        }
    }

    func loadRemoteBook(remoteBookId: Int64) {
        self.bookId = "remote-\(remoteBookId)"
        self.remoteBookId = remoteBookId
        self.isRemote = true
        self.settings = locator.readerSettingsStore.snapshot()
        self.snapshotSettings = settings
        self.brightness = brightnessControl.getBrightness()
        self.readingStartTime = Date()

        loadBookmarks()
        applyScreenAlwaysOn()

        Task {
            await loadRemoteBookById(remoteBookId)
            await MainActor.run { isLoading = false }
        }
    }

    private func loadLocalBook(bookId: String) async {
        guard let book = await MainActor.run(body: {
            locator.bookRepository.fetchBook(byId: bookId)
        }) else {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["找不到书籍"])]
            }
            return
        }

        let url = URL(fileURLWithPath: book.uri)

        switch book.format {
        case .epub: await loadEpub(url: url)
        case .txt: await loadTextFile(url: url)
        case .markdown: await loadMarkdown(url: url)
        case .html, .htm: await loadHtml(url: url)
        case .pdf:
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "提示", href: "", content: ["PDF 阅读即将推出"])]
            }
        }

        if let savedIndex = await MainActor.run(body: { book.progress.chapterIndex }),
           let idx = savedIndex, idx >= 0, idx < chapters.count {
            await MainActor.run { currentChapterIndex = idx }
        }
    }

    private func loadRemoteBook(bookId: String) async {
        // 从本地数据库查找远程书籍信息
        guard let book = await MainActor.run(body: {
            locator.bookRepository.fetchBook(byId: bookId)
        }),
              let rid = book.progress.chapterHref.flatMap({ Int64($0) }) ?? nil else {
            await loadRemoteBookById(0)
            return
        }
        await loadRemoteBookById(rid)
    }

    private func loadRemoteBookById(_ remoteId: Int64) async {
        await MainActor.run {
            chapters = [ReaderChapter(index: 0, title: "加载中", href: "", content: ["正在从服务器获取内容..."])]
        }

        guard remoteId > 0 else {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["无效的远程书籍 ID"])]
            }
            return
        }

        do {
            let content = try await locator.bookApi.getProcessedContent(id: remoteId)
            let paragraphs = content
                .components(separatedBy: "\n")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }

            // 分章
            let chunkSize = 50
            var result: [ReaderChapter] = []
            if paragraphs.count <= chunkSize {
                result = [ReaderChapter(index: 0, title: "全文", href: "remote-\(remoteId)", content: paragraphs)]
            } else {
                for i in stride(from: 0, to: paragraphs.count, by: chunkSize) {
                    let end = min(i + chunkSize, paragraphs.count)
                    let chunk = Array(paragraphs[i..<end])
                    result.append(ReaderChapter(
                        index: result.count,
                        title: "第 \(result.count + 1) 章",
                        href: "chapter-\(result.count)",
                        content: chunk
                    ))
                }
            }

            await MainActor.run {
                chapters = result.isEmpty
                    ? [ReaderChapter(index: 0, title: "空", href: "", content: ["服务器返回内容为空"])]
                    : result
            }
        } catch {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["加载失败: \(error.localizedDescription)"])]
            }
        }
    }

    // MARK: - EPUB 加载

    private func loadEpub(url: URL) async {
        do {
            let epub = try EpubParser.parse(url: url)
            let parsed = epub.chapters.enumerated().map { index, chapter in
                ReaderChapter(
                    index: index,
                    title: chapter.title ?? "第 \(index + 1) 章",
                    href: chapter.href,
                    content: splitIntoParagraphs(chapter.content)
                )
            }
            await MainActor.run {
                chapters = parsed.isEmpty
                    ? [ReaderChapter(index: 0, title: "空", href: "", content: ["本书暂无内容"])]
                    : parsed
            }
        } catch {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["EPUB 解析失败: \(error.localizedDescription)"])]
            }
        }
    }

    // MARK: - 纯文本加载

    private func loadTextFile(url: URL) async {
        guard let content = try? String(contentsOf: url, encoding: .utf8) ??
                (try? String(contentsOf: url, encoding: .ascii)) else {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["文件读取失败"])]
            }
            return
        }

        let paragraphs = splitIntoParagraphs(content)
        let chunkSize = 50
        var result: [ReaderChapter] = []

        if paragraphs.count <= chunkSize {
            result = [ReaderChapter(index: 0, title: "全文", href: "full", content: paragraphs)]
        } else {
            for i in stride(from: 0, to: paragraphs.count, by: chunkSize) {
                let end = min(i + chunkSize, paragraphs.count)
                result.append(ReaderChapter(
                    index: result.count,
                    title: "第 \(result.count + 1) 章",
                    href: "chapter-\(result.count)",
                    content: Array(paragraphs[i..<end])
                ))
            }
        }

        await MainActor.run { chapters = result }
    }

    // MARK: - Markdown 加载

    private func loadMarkdown(url: URL) async {
        guard let content = try? String(contentsOf: url, encoding: .utf8) else {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["文件读取失败"])]
            }
            return
        }

        let lines = content.components(separatedBy: .newlines)
        var result: [ReaderChapter] = []
        var currentTitle = "前言"
        var currentContent: [String] = []

        for line in lines {
            if line.hasPrefix("# ") || line.hasPrefix("## ") {
                if !currentContent.isEmpty {
                    result.append(ReaderChapter(index: result.count, title: currentTitle, href: "chapter-\(result.count)", content: currentContent.filter { !$0.isEmpty }))
                    currentContent = []
                }
                currentTitle = line.replacingOccurrences(of: "#", with: "").trimmingCharacters(in: .whitespaces)
            } else {
                currentContent.append(line)
            }
        }

        if !currentContent.isEmpty {
            result.append(ReaderChapter(index: result.count, title: currentTitle, href: "chapter-\(result.count)", content: currentContent.filter { !$0.isEmpty }))
        }

        await MainActor.run {
            chapters = result.isEmpty ? [ReaderChapter(index: 0, title: "空", href: "", content: ["暂无内容"])] : result
        }
    }

    // MARK: - HTML 加载

    private func loadHtml(url: URL) async {
        guard let html = try? String(contentsOf: url, encoding: .utf8) else {
            await MainActor.run {
                chapters = [ReaderChapter(index: 0, title: "错误", href: "", content: ["文件读取失败"])]
            }
            return
        }

        var text = html
        text = text.replacingOccurrences(of: "<(script|style)[^>]*>[\\s\\S]*?</\\1>", with: "", options: .regularExpression)
        text = text.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
        text = text.replacingOccurrences(of: "</p>", with: "\n\n", options: .regularExpression)
        text = text.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        text = text.replacingOccurrences(of: "&amp;", with: "&")
        text = text.replacingOccurrences(of: "&lt;", with: "<")
        text = text.replacingOccurrences(of: "&gt;", with: ">")
        text = text.replacingOccurrences(of: "&nbsp;", with: " ")

        let paragraphs = splitIntoParagraphs(text)
        await MainActor.run {
            chapters = [ReaderChapter(index: 0, title: "全文", href: "full", content: paragraphs)]
        }
    }

    // MARK: - 进度管理

    func saveProgress() {
        guard !chapters.isEmpty else { return }
        let chapter = chapters[currentChapterIndex]
        let progress = ReadingProgress(
            chapterHref: chapter.href,
            chapterTitle: chapter.title,
            chapterIndex: currentChapterIndex,
            lineIndex: 0,
            scrollOffset: 0,
            percent: Float(progressPercent),
            positionLabel: positionLabel
        )

        // 本地保存
        locator.bookRepository.updateProgress(bookId: bookId, progress: progress)

        // 远程同步
        if isRemote, let remoteId = remoteBookId ?? extractRemoteId() {
            Task {
                try? await locator.bookApi.saveReadingProgress(
                    bookId: remoteId,
                    progress: ReadingProgressRequest(
                        chapterIndex: currentChapterIndex,
                        chapterTitle: chapter.title,
                        progressPercent: progressPercent
                    )
                )
            }
        }
    }

    func syncReadingTime() {
        guard let startTime = readingStartTime else { return }
        let elapsed = Int(Date().timeIntervalSince(startTime))
        guard elapsed > 0 else { return }

        if isRemote, let remoteId = remoteBookId ?? extractRemoteId() {
            Task {
                try? await locator.bookApi.updateReadingTime(bookId: remoteId, seconds: elapsed)
            }
        }

        readingStartTime = Date()
    }

    private func extractRemoteId() -> Int64? {
        // 从 bookId 格式 "remote-{id}" 提取
        guard bookId.hasPrefix("remote-") else { return nil }
        return Int64(bookId.replacingOccurrences(of: "remote-", with: ""))
    }

    // MARK: - 章节导航

    func goToChapter(_ index: Int) {
        guard index >= 0, index < chapters.count else { return }
        currentChapterIndex = index
        saveProgress()
    }

    func nextChapter() { goToChapter(currentChapterIndex + 1) }
    func previousChapter() { goToChapter(currentChapterIndex - 1) }

    // MARK: - 书签

    func toggleBookmark() {
        guard let chapter = currentChapter else { return }
        if let idx = bookmarks.firstIndex(where: { $0.chapterHref == chapter.href }) {
            bookmarks.remove(at: idx)
        } else {
            let bookmark = ReaderBookmark(
                id: UUID().uuidString, bookId: bookId,
                chapterHref: chapter.href, chapterTitle: chapter.title,
                progress: progressPercent, createdAt: Date()
            )
            bookmarks.insert(bookmark, at: 0)
        }
        saveBookmarks()
    }

    func loadBookmarks() {
        let key = "bookmarks.\(bookId)"
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([ReaderBookmark].self, from: data) else { return }
        bookmarks = decoded
    }

    func saveBookmarks() {
        let key = "bookmarks.\(bookId)"
        if let data = try? JSONEncoder().encode(bookmarks) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    // MARK: - 亮度

    func setBrightness(_ value: CGFloat) {
        brightness = value
        brightnessControl.setBrightness(value)
    }

    func toggleAutoBrightness(_ enabled: Bool) {
        if enabled {
            brightnessControl.enableAutoBrightness()
            brightness = brightnessControl.getBrightness()
        } else {
            brightnessControl.disableAutoBrightness()
        }
    }

    // MARK: - 设置

    func applySettings() {
        locator.readerSettingsStore.apply(settings)
        applyScreenAlwaysOn()
    }

    func cancelSettings() {
        if let snapshot = snapshotSettings { settings = snapshot }
    }

    func cleanup() {
        syncReadingTime()
        brightnessControl.restore()
    }

    private func applyScreenAlwaysOn() {
        brightnessControl.setScreenAlwaysOn(settings.screenAlwaysOn)
    }

    private func splitIntoParagraphs(_ text: String) -> [String] {
        text.components(separatedBy: "\n\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}

private extension Array {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
