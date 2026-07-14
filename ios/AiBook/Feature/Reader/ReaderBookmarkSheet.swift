import SwiftUI

// MARK: - ReaderBookmarkSheet（书签管理面板）

@MainActor
struct ReaderBookmarkSheet: View {
    let bookId: String
    let currentChapterTitle: String
    let currentChapterHref: String
    let currentProgress: Double
    let onDismiss: () -> Void

    @State private var bookmarks: [ReaderBookmark] = []
    @State private var noteText: String = ""

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // 添加书签
                VStack(spacing: 8) {
                    HStack {
                        Image(systemName: "bookmark.fill")
                            .foregroundColor(DesignTokens.accent)
                        Text("当前位置: \(currentChapterTitle)")
                            .font(.subheadline)
                        Spacer()
                    }

                    HStack {
                        TextField("添加笔记（可选）", text: $noteText)
                            .textFieldStyle(.roundedBorder)

                        Button {
                            addBookmark()
                        } label: {
                            Text("添加")
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(DesignTokens.accent)
                                .cornerRadius(8)
                        }
                    }
                }
                .padding()
                .background(DesignTokens.warmCard)

                Divider()

                // 书签列表
                if bookmarks.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "bookmark")
                            .font(.system(size: 32))
                            .foregroundColor(DesignTokens.softText.opacity(0.4))
                        Text("暂无书签")
                            .foregroundColor(DesignTokens.softText)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(bookmarks) { bookmark in
                            VStack(alignment: .leading, spacing: 4) {
                                HStack {
                                    Text(bookmark.chapterTitle)
                                        .font(.subheadline.bold())
                                    Spacer()
                                    Text(formatDate(bookmark.createdAt))
                                        .font(.caption2)
                                        .foregroundColor(DesignTokens.softText)
                                }
                                Text("进度: \(Int(bookmark.progress * 100))%")
                                    .font(.caption)
                                    .foregroundColor(DesignTokens.softText)
                                if !(bookmark.note?.isEmpty ?? true) {
                                    Text(bookmark.note ?? "")
                                        .font(.caption)
                                        .foregroundColor(.primary)
                                        .padding(.top, 2)
                                }
                            }
                            .padding(.vertical, 4)
                        }
                        .onDelete { indexSet in
                            deleteBookmarks(at: indexSet)
                        }
                    }
                }
            }
            .navigationTitle("书签")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { onDismiss() }
                }
            }
        }
        .onAppear { loadBookmarks() }
    }

    // MARK: - 操作

    private func addBookmark() {
        let bookmark = ReaderBookmark(
            id: UUID().uuidString,
            bookId: bookId,
            chapterHref: currentChapterHref,
            chapterTitle: currentChapterTitle,
            progress: currentProgress,
            createdAt: Date()
        )
        bookmarks.insert(bookmark, at: 0)
        saveBookmarks()
        noteText = ""
    }

    private func deleteBookmarks(at offsets: IndexSet) {
        bookmarks.remove(atOffsets: offsets)
        saveBookmarks()
    }

    private func loadBookmarks() {
        let key = "bookmarks.\(bookId)"
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([ReaderBookmark].self, from: data) else { return }
        bookmarks = decoded
    }

    private func saveBookmarks() {
        let key = "bookmarks.\(bookId)"
        if let data = try? JSONEncoder().encode(bookmarks) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM-dd HH:mm"
        return formatter.string(from: date)
    }
}

// MARK: - ReaderBookmark Codable 扩展

extension ReaderBookmark: Codable {
    enum CodingKeys: String, CodingKey {
        case id, bookId, chapterHref, chapterTitle, progress, createdAt, note
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        bookId = try container.decode(String.self, forKey: .bookId)
        chapterHref = try container.decode(String.self, forKey: .chapterHref)
        chapterTitle = try container.decode(String.self, forKey: .chapterTitle)
        progress = try container.decode(Double.self, forKey: .progress)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        note = try container.decodeIfPresent(String.self, forKey: .note)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(bookId, forKey: .bookId)
        try container.encode(chapterHref, forKey: .chapterHref)
        try container.encode(chapterTitle, forKey: .chapterTitle)
        try container.encode(progress, forKey: .progress)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encodeIfPresent(note, forKey: .note)
    }
}
