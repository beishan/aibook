import SwiftUI

// MARK: - StoreRemoteBookDetailScreen（与安卓 StoreRemoteBookDetailScreen.kt 对齐 — 完整实现）

@MainActor
struct StoreRemoteBookDetailScreen: View {
    let bookId: String
    @Environment(ServiceLocator.self) private var locator
    @Environment(\.dismiss) private var dismiss

    @State private var book: BookDto?
    @State private var isLoading = true
    @State private var isDownloading = false
    @State private var errorMessage: String?

    var body: some View {
        Group {
            if isLoading {
                ProgressView("加载中...")
            } else if let book {
                bookContent(book)
            } else {
                ContentUnavailableView("书籍未找到", systemImage: "questionmark.book")
            }
        }
        .background(DesignTokens.appBackground)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadBook() }
    }

    @ViewBuilder
    private func bookContent(_ book: BookDto) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                // 封面
                RoundedRectangle(cornerRadius: 12)
                    .fill(DesignTokens.warmCard)
                    .frame(width: 160, height: 220)
                    .overlay(
                        VStack(spacing: 8) {
                            Image(systemName: "cloud.fill")
                                .font(.system(size: 40))
                                .foregroundColor(DesignTokens.opdsGreen.opacity(0.5))
                            Text(book.title)
                                .font(.caption)
                                .lineLimit(2)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 8)
                        }
                    )

                // 书名 / 作者
                VStack(spacing: 6) {
                    Text(book.title)
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)

                    if let author = book.author {
                        Text(author)
                            .font(.subheadline)
                            .foregroundColor(DesignTokens.softText)
                    }
                }

                // 信息
                VStack(spacing: 8) {
                    if let format = book.format {
                        infoRow("格式", format.uppercased())
                    }
                    if let size = book.fileSize {
                        infoRow("大小", ByteCountFormatter.string(fromByteCount: size, countStyle: .file))
                    }
                    if let pages = book.pageCount {
                        infoRow("页数", "\(pages) 页")
                    }
                }

                // 简介
                if let desc = book.description, !desc.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("简介")
                            .font(.headline)
                        Text(desc)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, DesignTokens.pagePadding)
                }

                // 操作按钮
                VStack(spacing: 12) {
                    Button {
                        Task { await downloadAndRead() }
                    } label: {
                        HStack {
                            if isDownloading {
                                ProgressView().scaleEffect(0.8)
                            } else {
                                Image(systemName: "arrow.down.circle.fill")
                            }
                            Text(isDownloading ? "下载中..." : "下载并阅读")
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(DesignTokens.accent)
                        .cornerRadius(DesignTokens.cardRadius)
                    }
                    .disabled(isDownloading)
                }
                .padding(.horizontal, DesignTokens.pagePadding)

                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal, DesignTokens.pagePadding)
                }

                Spacer(minLength: 40)
            }
        }
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption)
                .foregroundColor(DesignTokens.softText)
                .frame(width: 50, alignment: .leading)
            Text(value)
                .font(.caption)
            Spacer()
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    private func loadBook() {
        guard let remoteId = Int64(bookId) else {
            isLoading = false
            return
        }

        Task {
            do {
                let dto = try await locator.bookApi.getBookDetail(id: remoteId)
                await MainActor.run {
                    book = dto
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }

    private func downloadAndRead() async {
        guard let remoteId = Int64(bookId) else { return }

        await MainActor.run { isDownloading = true }

        do {
            // 获取处理后的内容
            let content = try await locator.bookApi.getProcessedContent(id: remoteId)

            // 保存为本地 TXT 文件
            let fileName = "\(book?.title ?? "remote-\(remoteId)").txt"
            let tempDir = FileManager.default.temporaryDirectory
            let tempFile = tempDir.appendingPathComponent(fileName)
            try content.write(to: tempFile, atomically: true, encoding: .utf8)

            // 导入到书库
            let result = await MainActor.run {
                locator.bookRepository.importBook(from: tempFile, fileName: fileName)
            }

            try? FileManager.default.removeItem(at: tempFile)

            await MainActor.run {
                isDownloading = false
                switch result {
                case .added, .restored:
                    // 设置远程关联
                    // TODO: 保存 remoteBookId 关联
                    dismiss()
                case .duplicate:
                    errorMessage = "书籍已存在"
                case .unsupported:
                    errorMessage = "不支持的格式"
                case .failed:
                    errorMessage = "保存失败"
                }
            }
        } catch {
            await MainActor.run {
                isDownloading = false
                errorMessage = "下载失败: \(error.localizedDescription)"
            }
        }
    }
}
