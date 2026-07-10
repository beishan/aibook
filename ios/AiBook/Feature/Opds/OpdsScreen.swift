import SwiftUI

// MARK: - OpdsScreen（与安卓 OpdsScreen.kt 对齐 — 完整实现）

struct OpdsScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var viewModel: OpdsViewModel?

    var body: some View {
        Group {
            if let vm = viewModel {
                if vm.isBrowsing {
                    catalogBrowser(vm)
                } else {
                    discoveryHome(vm)
                }
            } else {
                ProgressView()
                    .onAppear {
                        let vm = OpdsViewModel(locator: locator)
                        vm.load()
                        viewModel = vm
                    }
            }
        }
        .navigationTitle("发现")
        .navigationBarTitleDisplayMode(.large)
        .alert("错误", isPresented: Binding(
            get: { viewModel?.errorMessage != nil },
            set: { if !$0 { viewModel?.clearMessages() } }
        )) {
            Button("确定") { viewModel?.clearMessages() }
        } message: {
            Text(viewModel?.errorMessage ?? "")
        }
        .alert("成功", isPresented: Binding(
            get: { viewModel?.successMessage != nil },
            set: { if !$0 { viewModel?.clearMessages() } }
        )) {
            Button("确定") { viewModel?.clearMessages() }
        } message: {
            Text(viewModel?.successMessage ?? "")
        }
    }

    // MARK: - 发现首页

    @ViewBuilder
    private func discoveryHome(_ vm: OpdsViewModel) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                // 入口卡片
                entryCards

                // 同步全部按钮
                if vm.connections.contains(where: { $0.enabled }) {
                    Button {
                        Task { await vm.syncAll() }
                    } label: {
                        Label("同步全部书源", systemImage: "arrow.triangle.2.circlepath")
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(DesignTokens.opdsGreen)
                            .cornerRadius(DesignTokens.cardRadius)
                    }
                    .padding(.horizontal, DesignTokens.pagePadding)
                }

                // OPDS 连接列表
                connectionList(vm)
            }
            .padding(.vertical, 8)
        }
        .background(DesignTokens.appBackground)
    }

    // MARK: - 入口卡片

    private var entryCards: some View {
        VStack(spacing: 12) {
            NavigationLink(value: Screen.scanDirectories) {
                entryCard(
                    icon: "folder.badge.plus",
                    title: "扫描目录",
                    subtitle: "管理本地扫描目录",
                    color: DesignTokens.accent
                )
            }
            .buttonStyle(.plain)

            NavigationLink(value: Screen.opdsAddSource(connectionId: nil)) {
                entryCard(
                    icon: "plus.circle",
                    title: "添加 OPDS 书源",
                    subtitle: "连接在线图书馆",
                    color: DesignTokens.opdsGreen
                )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    private func entryCard(icon: String, title: String, subtitle: String, color: Color) -> some View {
        HStack {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
                .frame(width: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                Text(subtitle).font(.caption).foregroundColor(DesignTokens.softText)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundColor(DesignTokens.softText)
        }
        .padding()
        .background(DesignTokens.warmCard)
        .cornerRadius(DesignTokens.cardRadius)
    }

    // MARK: - 连接列表

    private func connectionList(_ vm: OpdsViewModel) -> some View {
        VStack(spacing: 12) {
            if !vm.connections.isEmpty {
                HStack {
                    Text("已添加的书源")
                        .font(.headline)
                    Spacer()
                    Text("\(vm.connections.count) 个")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                }
                .padding(.horizontal, DesignTokens.pagePadding)
            }

            if vm.connections.isEmpty {
                emptyState
            } else {
                ForEach(vm.connections) { conn in
                    connectionCard(conn, vm: vm)
                }
                .padding(.horizontal, DesignTokens.pagePadding)
            }
        }
    }

    private func connectionCard(_ conn: OpdsConnection, vm: OpdsViewModel) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // 标题行
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(conn.name)
                            .font(.subheadline.bold())
                        syncStateBadge(conn.syncState)
                    }
                    Text(conn.baseUrl)
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                        .lineLimit(1)
                }
                Spacer()

                if conn.bookCount > 0 {
                    Text("\(conn.bookCount) 本")
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(8)
                }
            }

            // 操作按钮
            HStack(spacing: 6) {
                opdsActionButton(
                    icon: "arrow.triangle.2.circlepath",
                    label: "同步",
                    isLoading: conn.syncState == .syncing
                ) {
                    Task { await vm.syncConnection(id: conn.id) }
                }
                .disabled(conn.syncState == .syncing)

                opdsActionButton(icon: "book", label: "浏览") {
                    Task { await vm.browseConnection(conn) }
                }

                NavigationLink(value: Screen.opdsAddSource(connectionId: conn.id)) {
                    Label("编辑", systemImage: "pencil")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(8)
                }

                Spacer()

                Button(role: .destructive) {
                    vm.deleteConnection(id: conn.id)
                } label: {
                    Image(systemName: "trash")
                        .font(.caption)
                        .foregroundColor(.red.opacity(0.7))
                        .padding(8)
                }
            }

            // 错误信息
            if conn.syncState == .failed, let error = conn.lastErrorMessage {
                Text(error)
                    .font(.caption2)
                    .foregroundColor(.red)
                    .lineLimit(2)
            }
        }
        .padding()
        .background(DesignTokens.cardBackground)
        .cornerRadius(DesignTokens.cardRadius)
        .shadow(color: .black.opacity(0.05), radius: 4, x: 0, y: 2)
    }

    private func opdsActionButton(icon: String, label: String, isLoading: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if isLoading {
                    ProgressView().scaleEffect(0.6)
                } else {
                    Image(systemName: icon)
                }
                Text(label)
            }
            .font(.caption)
            .foregroundColor(DesignTokens.softText)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(DesignTokens.warmCard)
            .cornerRadius(8)
        }
    }

    // MARK: - 目录浏览器

    @ViewBuilder
    private func catalogBrowser(_ vm: OpdsViewModel) -> some View {
        VStack(spacing: 0) {
            // 导航栏
            HStack {
                Button {
                    Task { await vm.navigateBack() }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("返回")
                    }
                    .font(.subheadline)
                }
                .disabled(!vm.canNavigateBack)

                Spacer()

                Text(vm.browsingTitle)
                    .font(.subheadline.bold())
                    .lineLimit(1)

                Spacer()

                Button {
                    vm.isBrowsing = false
                    vm.browsingEntries = []
                    vm.browsingSubLinks = []
                } label: {
                    Text("关闭")
                        .font(.subheadline)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(DesignTokens.warmCard)

            if vm.isLoading {
                Spacer()
                ProgressView("加载中...")
                Spacer()
            } else {
                ScrollView {
                    VStack(spacing: 12) {
                        // 子目录链接
                        if !vm.browsingSubLinks.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("分类")
                                    .font(.caption.bold())
                                    .foregroundColor(DesignTokens.softText)
                                    .padding(.horizontal, DesignTokens.pagePadding)

                                ForEach(Array(vm.browsingSubLinks.enumerated()), id: \.offset) { _, subLink in
                                    Button {
                                        Task { await vm.browseSubLink(subLink.href, title: subLink.title) }
                                    } label: {
                                        HStack {
                                            Image(systemName: "folder")
                                                .foregroundColor(DesignTokens.opdsGreen)
                                            Text(subLink.title)
                                                .font(.subheadline)
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .foregroundColor(DesignTokens.softText)
                                        }
                                        .padding()
                                        .background(DesignTokens.cardBackground)
                                        .cornerRadius(12)
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.horizontal, DesignTokens.pagePadding)
                                }
                            }
                        }

                        // 书籍列表
                        if !vm.browsingEntries.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("书籍 (\(vm.browsingEntries.count))")
                                    .font(.caption.bold())
                                    .foregroundColor(DesignTokens.softText)
                                    .padding(.horizontal, DesignTokens.pagePadding)

                                ForEach(Array(vm.browsingEntries.enumerated()), id: \.offset) { index, entry in
                                    bookEntryCard(entry, index: index, vm: vm)
                                }
                            }
                        }

                        // 空状态
                        if vm.browsingEntries.isEmpty && vm.browsingSubLinks.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "tray")
                                    .font(.system(size: 40))
                                    .foregroundColor(DesignTokens.softText.opacity(0.4))
                                Text("此目录为空")
                                    .foregroundColor(DesignTokens.softText)
                            }
                            .padding(.top, 60)
                        }
                    }
                    .padding(.vertical, 12)
                }
            }
        }
        .background(DesignTokens.appBackground)
    }

    private func bookEntryCard(_ entry: OpdsEntry, index: Int, vm: OpdsViewModel) -> some View {
        HStack(spacing: 12) {
            // 封面占位
            RoundedRectangle(cornerRadius: 6)
                .fill(DesignTokens.warmCard)
                .frame(width: 50, height: 70)
                .overlay(
                    Image(systemName: "book.closed")
                        .foregroundColor(DesignTokens.softText.opacity(0.4))
                )

            // 信息
            VStack(alignment: .leading, spacing: 4) {
                Text(entry.title)
                    .font(.subheadline)
                    .lineLimit(2)

                if let author = entry.author {
                    Text(author)
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                        .lineLimit(1)
                }

                if let summary = entry.summary {
                    Text(summary)
                        .font(.caption2)
                        .foregroundColor(DesignTokens.softText.opacity(0.7))
                        .lineLimit(2)
                }

                // 格式标识
                if let link = entry.acquisitionLink {
                    Text(formatLabel(link.type))
                        .font(.system(size: 9))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(4)
                }
            }

            Spacer()

            // 下载按钮
            Button {
                Task { await vm.downloadBook(entry) }
            } label: {
                if vm.downloadingBookId == entry.title {
                    ProgressView()
                        .scaleEffect(0.8)
                } else {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.title2)
                        .foregroundColor(DesignTokens.accent)
                }
            }
            .disabled(vm.downloadingBookId != nil)
        }
        .padding()
        .background(DesignTokens.cardBackground)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 1)
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    // MARK: - 空状态

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 48))
                .foregroundColor(DesignTokens.softText.opacity(0.3))
            Text("暂无 OPDS 书源")
                .font(.headline)
                .foregroundColor(DesignTokens.softText)
            Text("添加 OPDS 书源后可以浏览和下载在线书籍")
                .font(.caption)
                .foregroundColor(DesignTokens.softText.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .padding(.top, 40)
        .padding(.horizontal, 40)
    }

    // MARK: - 辅助

    private func syncStateBadge(_ state: OpdsSyncState) -> some View {
        Group {
            switch state {
            case .idle:
                Text("空闲").font(.caption2).foregroundColor(.gray)
            case .syncing:
                HStack(spacing: 4) {
                    ProgressView().scaleEffect(0.6)
                    Text("同步中").font(.caption2)
                }
            case .success:
                Text("已同步").font(.caption2).foregroundColor(DesignTokens.success)
            case .failed:
                Text("失败").font(.caption2).foregroundColor(.red)
            }
        }
    }

    private func formatLabel(_ mimeType: String?) -> String {
        guard let mt = mimeType?.lowercased() else { return "未知" }
        if mt.contains("epub") { return "EPUB" }
        if mt.contains("pdf") { return "PDF" }
        if mt.contains("html") { return "HTML" }
        if mt.contains("text") { return "TXT" }
        return "文件"
    }
}
