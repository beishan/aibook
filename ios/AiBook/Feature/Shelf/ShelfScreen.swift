import SwiftUI
import UniformTypeIdentifiers

// MARK: - ShelfScreen（与安卓 ShelfScreen.kt 对齐 — 完整实现）

@MainActor
struct ShelfScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var viewModel: ShelfViewModel?
    @State private var showImporter = false
    @State private var showNewFolderAlert = false
    @State private var newFolderName = ""
    @State private var showMoveToFolder = false
    @State private var importResult: ImportResult?

    var body: some View {
        Group {
            if let vm = viewModel {
                shelfContent(vm)
            } else {
                ProgressView()
                    .onAppear {
                        let vm = ShelfViewModel(locator: locator)
                        vm.load()
                        viewModel = vm
                    }
            }
        }
        .navigationTitle("书架")
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder
    private func shelfContent(_ vm: ShelfViewModel) -> some View {
        ScrollView {
            VStack(spacing: 16) {
                // 继续阅读卡片
                if let recent = vm.recentBook {
                    continueReadingCard(recent, vm: vm)
                }

                // 文件夹筛选
                folderChips(vm)

                // 排序 + 管理按钮
                sortAndManageBar(vm)

                // 书籍网格
                if vm.filteredBooks.isEmpty {
                    emptyState
                } else {
                    bookGrid(vm)
                }
            }
            .padding(.vertical, 8)
        }
        .background(DesignTokens.appBackground)
        .fileImporter(
            isPresented: $showImporter,
            allowedContentTypes: [
                UTType.epub,
                .plainText,
                .utf8PlainText,
                .html,
                .init(filenameExtension: "md") ?? .plainText,
            ],
            allowsMultipleSelection: true
        ) { result in
            if case .success(let urls) = result {
                vm.importBooks(from: urls)
            }
        }
        .alert("新建文件夹", isPresented: $showNewFolderAlert) {
            TextField("文件夹名称", text: $newFolderName)
            Button("取消", role: .cancel) { newFolderName = "" }
            Button("创建") {
                let name = newFolderName.trimmingCharacters(in: .whitespaces)
                if !name.isEmpty {
                    _ = vm.createFolder(name: name)
                    newFolderName = ""
                }
            }
        } message: {
            Text("输入文件夹名称")
        }
        .sheet(isPresented: $showMoveToFolder) {
            moveToFolderSheet(vm)
        }
        .toolbar {
            if vm.isManaging {
                ToolbarItemGroup(placement: .bottomBar) {
                    Button {
                        vm.bulkFavorite()
                    } label: {
                        Label("收藏", systemImage: "heart")
                    }
                    .disabled(vm.selectedIds.isEmpty)

                    Spacer()

                    Button {
                        showMoveToFolder = true
                    } label: {
                        Label("移动", systemImage: "folder")
                    }
                    .disabled(vm.selectedIds.isEmpty)

                    Spacer()

                    Button(role: .destructive) {
                        vm.bulkRemoveFromShelf()
                    } label: {
                        Label("移除", systemImage: "minus.circle")
                    }
                    .disabled(vm.selectedIds.isEmpty)
                }
            }
        }
    }

    // MARK: - 排序 + 管理栏

    private func sortAndManageBar(_ vm: ShelfViewModel) -> some View {
        HStack {
            Button {
                vm.cycleSort()
            } label: {
                Label(sortLabel(vm.sortOption), systemImage: "arrow.up.arrow.down")
                    .font(.subheadline)
                    .foregroundColor(DesignTokens.softText)
            }

            Spacer()

            Button {
                showImporter = true
            } label: {
                Image(systemName: "plus")
                    .font(.subheadline)
                    .foregroundColor(DesignTokens.accent)
            }
            .padding(.trailing, 8)

            Button {
                withAnimation {
                    vm.isManaging.toggle()
                    vm.selectedIds.removeAll()
                }
            } label: {
                Text(vm.isManaging ? "完成" : "管理")
                    .font(.subheadline)
                    .foregroundColor(DesignTokens.accent)
            }
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    // MARK: - 继续阅读卡片

    private func continueReadingCard(_ book: LocalBook, vm: ShelfViewModel) -> some View {
        NavigationLink(value: Screen.reader(bookId: book.id)) {
            HStack(spacing: 12) {
                BookCoverView(
                    title: book.title,
                    author: book.author,
                    format: book.format,
                    coverUri: book.coverUri,
                    width: 50,
                    height: 70
                )

                VStack(alignment: .leading, spacing: 4) {
                    Text("继续阅读")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                    Text(book.title)
                        .font(.headline)
                        .foregroundColor(.primary)
                        .lineLimit(1)
                    if let author = book.author {
                        Text(author)
                            .font(.subheadline)
                            .foregroundColor(DesignTokens.softText)
                            .lineLimit(1)
                    }
                    if book.progress.percent > 0 {
                        HStack(spacing: 6) {
                            ProgressView(value: Double(book.progress.percent))
                                .tint(DesignTokens.accent)
                                .frame(maxWidth: 100)
                            Text("\(Int(book.progress.percent * 100))%")
                                .font(.caption2)
                                .foregroundColor(DesignTokens.softText)
                        }
                    }
                }

                Spacer()

                Image(systemName: "play.fill")
                    .foregroundColor(DesignTokens.accent)
                    .font(.title3)
            }
            .padding(12)
            .background(DesignTokens.warmCard)
            .cornerRadius(DesignTokens.cardRadius)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    // MARK: - 文件夹 Chips

    private func folderChips(_ vm: ShelfViewModel) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                folderChip("全部", count: vm.books.count, selected: vm.selection == .all) {
                    vm.selection = .all
                }
                folderChip("未归类", count: vm.books.filter { $0.folderId == nil }.count,
                           selected: vm.selection == .unfiled) {
                    vm.selection = .unfiled
                }
                ForEach(vm.folders) { folder in
                    let count = vm.folderCounts[folder.id] ?? 0
                    folderChip(folder.name, count: count,
                               selected: vm.selection == .folder(folderId: folder.id)) {
                        vm.selection = .folder(folderId: folder.id)
                    }
                    .contextMenu {
                        Button {
                            _ = vm.createFolder(name: folder.name) // rename not yet supported
                        } label: {
                            Label("重命名", systemImage: "pencil")
                        }
                        Button(role: .destructive) {
                            locator.shelfFolderRepository.delete(id: folder.id)
                            vm.load()
                        } label: {
                            Label("删除文件夹", systemImage: "trash")
                        }
                    }
                }

                // 新建文件夹按钮
                Button {
                    showNewFolderAlert = true
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.subheadline)
                        .foregroundColor(DesignTokens.accent)
                }
            }
            .padding(.horizontal, DesignTokens.pagePadding)
        }
    }

    private func folderChip(_ label: String, count: Int, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(label)
                Text("\(count)")
                    .font(.caption2)
                    .opacity(0.7)
            }
            .font(.subheadline)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .foregroundColor(selected ? .white : DesignTokens.softText)
            .background(selected ? DesignTokens.accent : DesignTokens.warmCard)
            .cornerRadius(16)
        }
        .buttonStyle(.plain)
    }

    // MARK: - 书籍网格

    private func bookGrid(_ vm: ShelfViewModel) -> some View {
        let columns = [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())]

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(vm.filteredBooks) { book in
                bookItem(book, vm: vm)
            }
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    private func bookItem(_ book: LocalBook, vm: ShelfViewModel) -> some View {
        VStack(spacing: 6) {
            ZStack(alignment: .topLeading) {
                NavigationLink(value: Screen.bookDetail(bookId: book.id)) {
                    BookCoverView(
                        title: book.title,
                        author: book.author,
                        format: book.format,
                        coverUri: book.coverUri,
                        width: 100,
                        height: 140
                    )
                }
                .buttonStyle(.plain)
                .disabled(vm.isManaging)

                // 管理模式选中标记
                if vm.isManaging {
                    Image(systemName: vm.selectedIds.contains(book.id) ? "checkmark.circle.fill" : "circle")
                        .foregroundColor(vm.selectedIds.contains(book.id) ? DesignTokens.accent : .gray)
                        .font(.title3)
                        .background(Circle().fill(.white).padding(2))
                        .padding(4)
                        .onTapGesture {
                            vm.toggleSelection(bookId: book.id)
                        }
                }

                // 收藏标记
                if book.favorite && !vm.isManaging {
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            Image(systemName: "heart.fill")
                                .font(.caption2)
                                .foregroundColor(.red)
                                .padding(4)
                        }
                    }
                }
            }
            .frame(width: 100, height: 140)
            .onTapGesture {
                if vm.isManaging {
                    vm.toggleSelection(bookId: book.id)
                }
            }
            .onLongPressGesture(minimumDuration: 0.5) {
                if !vm.isManaging {
                    withAnimation {
                        vm.isManaging = true
                        vm.selectedIds.insert(book.id)
                    }
                }
            }

            Text(book.title)
                .font(.caption)
                .lineLimit(1)
                .frame(width: 100)
        }
    }

    // MARK: - 移入文件夹 Sheet

    private func moveToFolderSheet(_ vm: ShelfViewModel) -> some View {
        NavigationStack {
            List {
                Section("选择文件夹") {
                    Button {
                        vm.bulkMoveToFolder(folderId: nil)
                        showMoveToFolder = false
                    } label: {
                        Label("未归类", systemImage: "tray")
                    }

                    ForEach(vm.folders) { folder in
                        Button {
                            vm.bulkMoveToFolder(folderId: folder.id)
                            showMoveToFolder = false
                        } label: {
                            Label(folder.name, systemImage: "folder")
                        }
                    }
                }

                Section {
                    Button {
                        showMoveToFolder = false
                        showNewFolderAlert = true
                    } label: {
                        Label("新建文件夹", systemImage: "plus.circle")
                            .foregroundColor(DesignTokens.accent)
                    }
                }
            }
            .navigationTitle("移入文件夹")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showMoveToFolder = false }
                }
            }
        }
    }

    // MARK: - 空状态

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "book.closed")
                .font(.system(size: 48))
                .foregroundColor(DesignTokens.softText.opacity(0.3))

            Text("书架还是空的")
                .font(.headline)
                .foregroundColor(DesignTokens.softText)

            Button {
                showImporter = true
            } label: {
                Label("导入书籍", systemImage: "plus")
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(DesignTokens.accent)
                    .cornerRadius(24)
            }
        }
        .padding(.top, 60)
    }

    private func sortLabel(_ option: ShelfSortOption) -> String {
        switch option {
        case .recentRead: return "最近阅读"
        case .importedAt: return "导入时间"
        case .title: return "书名"
        case .favoriteFirst: return "收藏优先"
        }
    }
}
