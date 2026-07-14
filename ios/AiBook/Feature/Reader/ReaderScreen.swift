import SwiftUI

// MARK: - ReaderScreen（与安卓 ReaderScreen.kt 对齐 — 完整实现含书签/主题/亮度）

@MainActor
struct ReaderScreen: View {
    let bookId: String
    let isRemote: Bool
    @Environment(ServiceLocator.self) private var locator
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: ReaderViewModel?

    var body: some View {
        Group {
            if let vm = viewModel {
                if vm.isLoading {
                    loadingView
                } else {
                    readerContent(vm)
                }
            } else {
                Color.black.ignoresSafeArea()
                    .onAppear {
                        let vm = ReaderViewModel(locator: locator)
                        vm.loadBook(bookId: bookId, isRemote: isRemote)
                        viewModel = vm
                    }
            }
        }
        .navigationBarHidden(true)
        .statusBarHidden(true)
    }

    // MARK: - 加载中

    private var loadingView: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                ProgressView().tint(.white)
                Text("加载中...")
                    .foregroundColor(.white.opacity(0.7))
                    .font(.subheadline)
            }
        }
    }

    // MARK: - 阅读主视图

    @ViewBuilder
    private func readerContent(_ vm: ReaderViewModel) -> some View {
        ZStack {
            // 阅读背景
            backgroundColor(vm.settings.theme)
                .ignoresSafeArea()

            // 阅读内容
            if vm.settings.pageTurnMode.usesPagedReading {
                pagedReader(vm)
            } else {
                verticalReader(vm)
            }

            // 控制栏
            if vm.showControls {
                readerControls(vm)
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.2)) {
                vm.showControls.toggle()
            }
        }
        // 目录
        .sheet(isPresented: Binding(
            get: { vm.showContents },
            set: { vm.showContents = $0 }
        )) {
            contentsSheet(vm)
        }
        // 设置
        .sheet(isPresented: Binding(
            get: { vm.showSettings },
            set: { vm.showSettings = $0; if !$0 { vm.cancelSettings() } }
        )) {
            settingsSheet(vm)
        }
        // 主题
        .sheet(isPresented: Binding(
            get: { vm.showThemeSheet },
            set: { vm.showThemeSheet = $0 }
        )) {
            ReaderThemeSheet(
                theme: Binding(
                    get: { vm.settings.theme },
                    set: { vm.settings.theme = $0; vm.applySettings() }
                )
            ) {
                vm.showThemeSheet = false
            }
        }
        // 书签
        .sheet(isPresented: Binding(
            get: { vm.showBookmarkSheet },
            set: { vm.showBookmarkSheet = $0 }
        )) {
            if let chapter = vm.currentChapter {
                ReaderBookmarkSheet(
                    bookId: bookId,
                    currentChapterTitle: chapter.title,
                    currentChapterHref: chapter.href,
                    currentProgress: vm.progressPercent
                ) {
                    vm.showBookmarkSheet = false
                    vm.loadBookmarks()
                }
            }
        }
        .onDisappear {
            vm.saveProgress()
            vm.cleanup()
        }
    }

    // MARK: - 垂直滚动阅读

    private func verticalReader(_ vm: ReaderViewModel) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: paragraphSpacing(vm.settings)) {
                ForEach(Array(vm.chapters.enumerated()), id: \.element.id) { index, chapter in
                    if index == vm.currentChapterIndex {
                        // 章节标题
                        Text(chapter.title)
                            .font(readerFont(vm.settings, isTitle: true))
                            .foregroundColor(textColor(vm.settings.theme))
                            .padding(.bottom, 12)
                            .padding(.horizontal, 20)

                        // 段落
                        ForEach(Array(chapter.content.enumerated()), id: \.offset) { _, paragraph in
                            Text(paragraph)
                                .font(readerFont(vm.settings))
                                .lineSpacing(lineSpacing(vm.settings))
                                .foregroundColor(textColor(vm.settings.theme))
                                .padding(.horizontal, 20)
                        }
                    }
                }
            }
            .padding(.vertical, 20)
            .padding(.bottom, 60)
        }
    }

    // MARK: - 分页阅读（带翻页动画）

    private func pagedReader(_ vm: ReaderViewModel) -> some View {
        AnimatedPageView(
            mode: vm.settings.pageTurnMode,
            pageCount: vm.chapters.count,
            currentPage: Binding(
                get: { vm.currentChapterIndex },
                set: { newValue in
                    vm.currentChapterIndex = newValue
                    vm.saveProgress()
                }
            )
        ) { index in
            ScrollView {
                VStack(alignment: .leading, spacing: paragraphSpacing(vm.settings)) {
                    let chapter = vm.chapters[index]

                    Text(chapter.title)
                        .font(readerFont(vm.settings, isTitle: true))
                        .foregroundColor(textColor(vm.settings.theme))
                        .padding(.bottom, 12)

                    ForEach(Array(chapter.content.enumerated()), id: \.offset) { _, paragraph in
                        Text(paragraph)
                            .font(readerFont(vm.settings))
                            .lineSpacing(lineSpacing(vm.settings))
                            .foregroundColor(textColor(vm.settings.theme))
                    }
                }
                .padding(20)
                .padding(.top, 50) // 为顶部控制栏留空
                .padding(.bottom, 80) // 为底部控制栏留空
            }
        }
    }

    // MARK: - 控制栏

    private func readerControls(_ vm: ReaderViewModel) -> some View {
        VStack(spacing: 0) {
            // 顶部栏
            topBar(vm)
                .background(
                    LinearGradient(
                        colors: [Color.black.opacity(0.6), .clear],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )

            Spacer()

            // 底部栏
            bottomBar(vm)
                .background(
                    LinearGradient(
                        colors: [.clear, Color.black.opacity(0.6)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
        }
    }

    // MARK: - 顶部控制栏

    private func topBar(_ vm: ReaderViewModel) -> some View {
        HStack {
            // 返回
            Button {
                vm.saveProgress()
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3)
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
            }

            Spacer()

            // 章节标题
            Text(vm.currentChapter?.title ?? "")
                .font(.subheadline)
                .foregroundColor(.white)
                .lineLimit(1)

            Spacer()

            // 书签按钮
            Button {
                vm.toggleBookmark()
            } label: {
                Image(systemName: vm.isCurrentChapterBookmarked ? "bookmark.fill" : "bookmark")
                    .font(.title3)
                    .foregroundColor(vm.isCurrentChapterBookmarked ? DesignTokens.accent : .white)
                    .frame(width: 44, height: 44)
            }

            // 目录按钮
            Button {
                vm.showContents = true
            } label: {
                Image(systemName: "list.bullet")
                    .font(.title3)
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 8)
    }

    // MARK: - 底部控制栏

    private func bottomBar(_ vm: ReaderViewModel) -> some View {
        VStack(spacing: 12) {
            // 进度滑块
            HStack(spacing: 8) {
                Text("\(vm.currentChapterIndex + 1)")
                    .font(.caption)
                    .foregroundColor(.white)
                    .frame(width: 24)

                Slider(
                    value: Binding(
                        get: { Double(vm.currentChapterIndex) },
                        set: { vm.goToChapter(Int($0)) }
                    ),
                    in: 0...Double(max(vm.chapters.count - 1, 1)),
                    step: 1
                )
                .tint(.white)

                Text("\(vm.chapters.count)")
                    .font(.caption)
                    .foregroundColor(.white)
                    .frame(width: 24)
            }

            // 功能按钮
            HStack(spacing: 0) {
                readerControlButton("textformat.size", label: "设置") { vm.showSettings = true }
                readerControlButton("chevron.left", label: "上一章") { vm.previousChapter() }
                readerControlButton("paintbrush", label: "主题") { vm.showThemeSheet = true }
                readerControlButton("chevron.right", label: "下一章") { vm.nextChapter() }
                readerControlButton("bookmark", label: "书签") { vm.showBookmarkSheet = true }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }

    private func readerControlButton(_ systemName: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: systemName)
                    .font(.body)
                Text(label)
                    .font(.system(size: 9))
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .contentShape(Rectangle())
        }
    }

    // MARK: - 目录 Sheet

    private func contentsSheet(_ vm: ReaderViewModel) -> some View {
        NavigationStack {
            List(Array(vm.chapters.enumerated()), id: \.element.id) { index, chapter in
                Button {
                    vm.goToChapter(index)
                    vm.showContents = false
                } label: {
                    HStack {
                        Text(chapter.title)
                            .foregroundColor(index == vm.currentChapterIndex ? DesignTokens.accent : .primary)
                        Spacer()
                        if index == vm.currentChapterIndex {
                            Image(systemName: "checkmark")
                                .foregroundColor(DesignTokens.accent)
                        }
                        // 书签标记
                        if vm.bookmarks.contains(where: { $0.chapterHref == chapter.href }) {
                            Image(systemName: "bookmark.fill")
                                .font(.caption)
                                .foregroundColor(DesignTokens.accent)
                        }
                    }
                    .contentShape(Rectangle())
                }
            }
            .navigationTitle("目录")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { vm.showContents = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - 设置 Sheet

    private func settingsSheet(_ vm: ReaderViewModel) -> some View {
        NavigationStack {
            Form {
                // 亮度调节
                Section("亮度") {
                    BrightnessSlider(
                        brightness: Binding(
                            get: { vm.brightness },
                            set: { vm.setBrightness($0) }
                        ),
                        onAutoToggle: { vm.toggleAutoBrightness($0) }
                    )
                }

                // 字号
                Section("字号") {
                    HStack {
                        Text("A").font(.caption)
                        Slider(value: Binding(
                            get: { Double(vm.settings.fontScale) },
                            set: { vm.settings.fontScale = Float($0) }
                        ), in: 0.8...2.0, step: 0.1)
                        Text("A").font(.title3.bold())
                    }
                }

                // 行间距
                Section("行间距") {
                    HStack {
                        Text("紧凑").font(.caption).foregroundColor(DesignTokens.softText)
                        Slider(value: Binding(
                            get: { Double(vm.settings.lineHeight) },
                            set: { vm.settings.lineHeight = Float($0) }
                        ), in: 1.0...2.5, step: 0.1)
                        Text("宽松").font(.caption).foregroundColor(DesignTokens.softText)
                    }
                }

                // 段间距
                Section("段间距") {
                    ForEach(ParagraphSpacing.allCases, id: \.self) { spacing in
                        Button {
                            vm.settings.paragraphSpacing = spacing
                        } label: {
                            HStack {
                                Text(paragraphSpacingLabel(spacing))
                                Spacer()
                                if vm.settings.paragraphSpacing == spacing {
                                    Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .foregroundColor(.primary)
                    }
                }

                // 字体
                Section("字体") {
                    ForEach(ReaderFontCatalog.builtInFonts, id: \.type) { option in
                        Button {
                            vm.settings.fontType = option.type
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(option.label)
                                    Text(option.description).font(.caption).foregroundColor(DesignTokens.softText)
                                }
                                Spacer()
                                if vm.settings.fontType == option.type {
                                    Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .foregroundColor(.primary)
                    }
                }

                // 翻页模式
                Section("翻页模式") {
                    ForEach(PageTurnMode.allCases, id: \.self) { mode in
                        Button {
                            vm.settings.pageTurnMode = mode
                        } label: {
                            HStack {
                                Text(pageTurnLabel(mode))
                                Spacer()
                                if vm.settings.pageTurnMode == mode {
                                    Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .foregroundColor(.primary)
                    }
                }

                // 屏幕常亮
                Section {
                    Toggle("屏幕常亮", isOn: Binding(
                        get: { vm.settings.screenAlwaysOn },
                        set: { vm.settings.screenAlwaysOn = $0 }
                    ))
                }
            }
            .navigationTitle("阅读设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { vm.cancelSettings(); vm.showSettings = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { vm.applySettings(); vm.showSettings = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - 排版计算

    private func readerFont(_ settings: ReaderSettings, isTitle: Bool = false) -> Font {
        let baseSize: CGFloat = isTitle ? 20 : 16
        let size = CGFloat(settings.fontScale) * baseSize
        switch settings.fontType {
        case .system: return .system(size: size, weight: isTitle ? .semibold : .regular)
        case .serif: return .system(size: size, design: .serif)
        case .sansSerif: return .system(size: size, design: .default)
        case .monospace: return .system(size: size, design: .monospaced)
        case .custom: return .system(size: size) // TODO: 加载自定义字体
        }
    }

    private func lineSpacing(_ settings: ReaderSettings) -> CGFloat {
        CGFloat(settings.lineHeight * 14 - 14)
    }

    private func paragraphSpacing(_ settings: ReaderSettings) -> CGFloat {
        switch settings.paragraphSpacing {
        case .none: return 0
        case .small: return 8
        case .large: return 16
        }
    }

    private func textColor(_ theme: ReaderTheme) -> Color {
        switch theme {
        case .light, .paper: return Color(hex: 0xFF333333)
        case .green: return Color(hex: 0xFF3E5E3F)
        case .gray: return Color(hex: 0xFF333333)
        case .dark: return Color(hex: 0xFFCCCCCC)
        }
    }

    private func backgroundColor(_ theme: ReaderTheme) -> Color {
        switch theme {
        case .light: return .white
        case .paper: return Color(hex: 0xFFF5E6D3)
        case .green: return Color(hex: 0xFFCCE8CF)
        case .gray: return Color(hex: 0xFFE6E6E6)
        case .dark: return Color(hex: 0xFF1A1A1A)
        }
    }

    private func pageTurnLabel(_ mode: PageTurnMode) -> String {
        switch mode {
        case .simulation: return "仿真翻页"
        case .slide: return "滑动翻页"
        case .cover: return "覆盖翻页"
        case .pan: return "平移翻页"
        case .vertical: return "垂直滚动"
        }
    }

    private func paragraphSpacingLabel(_ spacing: ParagraphSpacing) -> String {
        switch spacing {
        case .none: return "无间距"
        case .small: return "小间距"
        case .large: return "大间距"
        }
    }
}
