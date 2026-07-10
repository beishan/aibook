import SwiftUI

// MARK: - BookStoreScreen（与安卓 BookStoreScreen.kt 对齐 — 完整实现）

struct BookStoreScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var viewModel: StoreViewModel?

    var body: some View {
        Group {
            if let vm = viewModel {
                storeContent(vm)
            } else {
                ProgressView()
                    .onAppear {
                        let vm = StoreViewModel(locator: locator)
                        vm.load()
                        viewModel = vm
                    }
            }
        }
        .navigationTitle("书城")
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder
    private func storeContent(_ vm: StoreViewModel) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                // 工具栏
                toolbar(vm)

                // 当前筛选条件
                if hasActiveFilter(vm) {
                    activeFilterChips(vm)
                }

                // 书籍列表
                if vm.filteredBooks.isEmpty {
                    emptyState(vm)
                } else {
                    bookContent(vm)
                }
            }
            .padding(.vertical, 8)
        }
        .background(DesignTokens.appBackground)
        .toolbar {
            if vm.isManaging {
                ToolbarItemGroup(placement: .bottomBar) {
                    Button(role: .destructive) {
                        vm.bulkDelete()
                    } label: {
                        Label("删除选中", systemImage: "trash")
                    }
                    .disabled(vm.selectedIds.isEmpty)
                }
            }
        }
    }

    // MARK: - 工具栏

    private func toolbar(_ vm: StoreViewModel) -> some View {
        HStack {
            // 视图模式切换
            Menu {
                ForEach(StoreViewMode.allCases, id: \.self) { mode in
                    Button {
                        vm.viewMode = mode
                    } label: {
                        Label(viewModeLabel(mode), systemImage: viewModeIcon(mode))
                    }
                }
            } label: {
                Image(systemName: viewModeIcon(vm.viewMode))
                    .foregroundColor(DesignTokens.softText)
                    .frame(width: 36, height: 36)
                    .background(DesignTokens.warmCard)
                    .cornerRadius(8)
            }

            Spacer()

            // 统计
            Text("\(vm.filteredBooks.count) 本书")
                .font(.caption)
                .foregroundColor(DesignTokens.softText)

            Spacer()

            // 筛选
            NavigationLink(value: Screen.storeCategory) {
                Image(systemName: hasActiveFilter(vm) ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                    .foregroundColor(hasActiveFilter(vm) ? DesignTokens.accent : DesignTokens.softText)
                    .frame(width: 36, height: 36)
                    .background(DesignTokens.warmCard)
                    .cornerRadius(8)
            }

            // 管理
            Button {
                withAnimation {
                    vm.isManaging.toggle()
                    vm.selectedIds.removeAll()
                }
            } label: {
                Text(vm.isManaging ? "完成" : "管理")
                    .font(.caption)
                    .foregroundColor(DesignTokens.accent)
            }
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    // MARK: - 筛选条件 Chips

    private func activeFilterChips(_ vm: StoreViewModel) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if let query = vm.filter.query, !query.isEmpty {
                    filterChip("搜索: \(query)") {
                        vm.filter.query = nil
                        vm.applyFilter()
                    }
                }
                if let format = vm.filter.format {
                    filterChip(format.displayName) {
                        vm.filter.format = nil
                        vm.applyFilter()
                    }
                }
                if let sourceId = vm.filter.sourceId {
                    let name = vm.sources.first(where: { $0.id == sourceId })?.name ?? sourceId
                    filterChip("来源: \(name)") {
                        vm.filter.sourceId = nil
                        vm.applyFilter()
                    }
                }

                Button {
                    vm.clearFilter()
                } label: {
                    Text("清除全部")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                }
            }
            .padding(.horizontal, DesignTokens.pagePadding)
        }
    }

    private func filterChip(_ label: String, onRemove: @escaping () -> Void) -> some View {
        HStack(spacing: 4) {
            Text(label).font(.caption)
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption2)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(DesignTokens.accent.opacity(0.1))
        .foregroundColor(DesignTokens.accent)
        .cornerRadius(12)
    }

    // MARK: - 书籍内容

    @ViewBuilder
    private func bookContent(_ vm: StoreViewModel) -> some View {
        switch vm.viewMode {
        case .grid:
            bookGrid(vm, columns: 3)
        case .smallGrid:
            bookGrid(vm, columns: 4)
        case .listCover:
            bookListCover(vm)
        case .compactList:
            bookCompactList(vm)
        }
    }

    // MARK: - 网格视图

    private func bookGrid(_ vm: StoreViewModel, columns: Int) -> some View {
        let gridColumns = Array(repeating: GridItem(.flexible()), count: columns)
        let coverSize: CGFloat = columns == 3 ? 100 : 75

        return LazyVGrid(columns: gridColumns, spacing: 16) {
            ForEach(vm.filteredBooks) { book in
                bookGridItem(book, vm: vm, coverSize: coverSize)
            }
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    private func bookGridItem(_ book: StoreBook, vm: StoreViewModel, coverSize: CGFloat) -> some View {
        VStack(spacing: 4) {
            ZStack(alignment: .topLeading) {
                if book.kind == .local, let localId = book.downloadedLocalId {
                    NavigationLink(value: Screen.bookDetail(bookId: localId)) {
                        BookCoverView(
                            title: book.title,
                            author: book.author,
                            format: book.format,
                            coverUri: book.coverUri,
                            width: coverSize,
                            height: coverSize * 1.4
                        )
                    }
                    .buttonStyle(.plain)
                } else {
                    BookCoverView(
                        title: book.title,
                        author: book.author,
                        format: book.format,
                        coverUri: book.coverUri,
                        width: coverSize,
                        height: coverSize * 1.4
                    )
                    .onTapGesture {
                        Task { await vm.downloadOpdsBook(book) }
                    }
                }

                // 管理模式选中标记
                if vm.isManaging && book.kind == .local {
                    Image(systemName: vm.selectedIds.contains(book.id) ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(vm.selectedIds.contains(book.id) ? DesignTokens.accent : .gray)
                        .font(.title3)
                        .background(Circle().fill(.white).padding(2))
                        .padding(4)
                        .onTapGesture { vm.toggleSelection(bookId: book.id) }
                }

                // OPDS 标记
                if book.kind == .opds {
                    VStack {
                        HStack {
                            Spacer()
                            Image(systemName: "cloud")
                                .font(.caption2)
                                .foregroundColor(.white)
                                .padding(4)
                                .background(Circle().fill(DesignTokens.opdsGreen))
                        }
                        Spacer()
                    }
                    .padding(4)
                }
            }
            .frame(width: coverSize, height: coverSize * 1.4)

            Text(book.title)
                .font(.caption)
                .lineLimit(1)
                .frame(width: coverSize)
        }
    }

    // MARK: - 带封面列表

    private func bookListCover(_ vm: StoreViewModel) -> some View {
        VStack(spacing: 8) {
            ForEach(vm.filteredBooks) { book in
                HStack(spacing: 12) {
                    BookCoverView(
                        title: book.title,
                        author: book.author,
                        format: book.format,
                        coverUri: book.coverUri,
                        width: 45,
                        height: 63
                    )

                    VStack(alignment: .leading, spacing: 3) {
                        Text(book.title).font(.subheadline).lineLimit(1)
                        Text(book.author ?? "").font(.caption).foregroundColor(DesignTokens.softText)
                        if book.kind == .opds {
                            Text(book.sourceName ?? "OPDS")
                                .font(.caption2)
                                .foregroundColor(DesignTokens.opdsGreen)
                        }
                    }

                    Spacer()

                    Text(book.format.displayName)
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(4)
                }
                .padding(.horizontal, DesignTokens.pagePadding)
                .contentShape(Rectangle())
                .onTapGesture {
                    if book.kind == .local, let localId = book.downloadedLocalId {
                        // 导航到详情
                    } else {
                        Task { await vm.downloadOpdsBook(book) }
                    }
                }
            }
        }
    }

    // MARK: - 紧凑列表

    private func bookCompactList(_ vm: StoreViewModel) -> some View {
        VStack(spacing: 0) {
            ForEach(vm.filteredBooks) { book in
                HStack {
                    Text(book.title)
                        .font(.subheadline)
                        .lineLimit(1)
                    Spacer()
                    Text(book.author ?? "")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                    Text(book.format.displayName)
                        .font(.caption2)
                        .foregroundColor(DesignTokens.softText)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(4)
                    if book.kind == .opds {
                        Image(systemName: "cloud")
                            .font(.caption2)
                            .foregroundColor(DesignTokens.opdsGreen)
                    }
                }
                .padding(.horizontal, DesignTokens.pagePadding)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
                Divider().padding(.horizontal, DesignTokens.pagePadding)
            }
        }
    }

    // MARK: - 空状态

    private func emptyState(_ vm: StoreViewModel) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "books.vertical")
                .font(.system(size: 48))
                .foregroundColor(DesignTokens.softText.opacity(0.3))
            Text(hasActiveFilter(vm) ? "没有符合条件的书籍" : "书城暂无书籍")
                .font(.headline)
                .foregroundColor(DesignTokens.softText)
            if hasActiveFilter(vm) {
                Button("清除筛选") { vm.clearFilter() }
                    .font(.subheadline)
                    .foregroundColor(DesignTokens.accent)
            }
        }
        .padding(.top, 60)
    }

    // MARK: - 辅助

    private func hasActiveFilter(_ vm: StoreViewModel) -> Bool {
        vm.filter.query != nil || vm.filter.format != nil || vm.filter.sourceId != nil
    }

    private func viewModeLabel(_ mode: StoreViewMode) -> String {
        switch mode {
        case .grid: return "网格"
        case .listCover: return "列表"
        case .compactList: return "紧凑"
        case .smallGrid: return "小网格"
        }
    }

    private func viewModeIcon(_ mode: StoreViewMode) -> String {
        switch mode {
        case .grid: return "square.grid.3x3"
        case .listCover: return "list.bullet"
        case .compactList: return "list.dash"
        case .smallGrid: return "square.grid.2x2"
        }
    }
}
