import SwiftUI

// MARK: - BookDetailScreen（与安卓 BookDetailScreen.kt 对齐 — 完整实现）

@MainActor
struct BookDetailScreen: View {
    let bookId: String
    @Environment(ServiceLocator.self) private var locator
    @Environment(\.dismiss) private var dismiss

    @State private var book: LocalBook?

    var body: some View {
        Group {
            if let book {
                bookContent(book)
            } else {
                ProgressView()
                    .onAppear { loadBook() }
            }
        }
        .background(DesignTokens.appBackground)
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder
    private func bookContent(_ book: LocalBook) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                // 封面
                BookCoverView(
                    title: book.title,
                    author: book.author,
                    format: book.format,
                    coverUri: book.coverUri,
                    width: 160,
                    height: 224
                )
                .padding(.top, 24)

                // 书名 / 作者
                VStack(spacing: 6) {
                    Text(book.title)
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)

                    if let author = book.author, !author.isEmpty {
                        Text(author)
                            .font(.subheadline)
                            .foregroundColor(DesignTokens.softText)
                    }
                }

                // 格式标识 + 状态
                HStack(spacing: 8) {
                    Text(book.format.displayName)
                        .font(.caption.weight(.medium))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(DesignTokens.warmCard)
                        .cornerRadius(8)

                    Text(statusLabel(book.status))
                        .font(.caption)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(statusColor(book.status).opacity(0.12))
                        .foregroundColor(statusColor(book.status))
                        .cornerRadius(8)
                }

                // 进度
                if book.status == .reading {
                    VStack(spacing: 6) {
                        ProgressView(value: Double(book.progress.percent))
                            .tint(DesignTokens.accent)
                            .frame(maxWidth: 200)
                        Text(book.progress.positionLabel ?? "已读 \(Int(book.progress.percent * 100))%")
                            .font(.caption)
                            .foregroundColor(DesignTokens.softText)
                    }
                }

                // 操作按钮
                VStack(spacing: 12) {
                    NavigationLink(value: Screen.reader(bookId: book.id)) {
                        HStack {
                            Image(systemName: "book.fill")
                            Text(book.status == .reading ? "继续阅读" : "开始阅读")
                        }
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(DesignTokens.accent)
                        .cornerRadius(DesignTokens.cardRadius)
                    }
                    .buttonStyle(.plain)

                    HStack(spacing: 12) {
                        // 收藏
                        Button {
                            locator.bookRepository.updateFavorite(bookId: book.id, favorite: !book.favorite)
                            loadBook()
                        } label: {
                            Label(
                                book.favorite ? "已收藏" : "收藏",
                                systemImage: book.favorite ? "heart.fill" : "heart"
                            )
                            .font(.subheadline)
                            .foregroundColor(book.favorite ? .red : DesignTokens.softText)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(DesignTokens.warmCard)
                            .cornerRadius(12)
                        }

                        // 书架
                        Button {
                            locator.bookRepository.updateShelved(bookId: book.id, shelved: !book.shelved)
                            if !book.shelved { dismiss() }
                            loadBook()
                        } label: {
                            Label(
                                book.shelved ? "移出书架" : "加入书架",
                                systemImage: book.shelved ? "minus.circle" : "plus.circle"
                            )
                            .font(.subheadline)
                            .foregroundColor(DesignTokens.softText)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(DesignTokens.warmCard)
                            .cornerRadius(12)
                        }
                    }

                    // 删除
                    Button(role: .destructive) {
                        locator.bookRepository.deleteBook(bookId: book.id)
                        dismiss()
                    } label: {
                        Label("删除", systemImage: "trash")
                            .font(.subheadline)
                            .foregroundColor(.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                }
                .padding(.horizontal, DesignTokens.pagePadding)

                // 书籍信息
                VStack(alignment: .leading, spacing: 8) {
                    Text("书籍信息")
                        .font(.subheadline.bold())
                        .padding(.horizontal, DesignTokens.pagePadding)

                    infoRow("格式", book.format.displayName)
                    infoRow("导入时间", formatDate(book.importedAt))
                    if let lastRead = book.lastReadAt {
                        infoRow("最近阅读", formatDate(lastRead))
                    }
                    if let sha = book.sha256, !sha.isEmpty {
                        infoRow("SHA-256", String(sha.prefix(16)) + "...")
                    }
                }
                .padding(.top, 8)

                Spacer(minLength: 40)
            }
        }
    }

    // MARK: - 信息行

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption)
                .foregroundColor(DesignTokens.softText)
                .frame(width: 70, alignment: .leading)
            Text(value)
                .font(.caption)
                .foregroundColor(.primary)
            Spacer()
        }
        .padding(.horizontal, DesignTokens.pagePadding)
    }

    // MARK: - 辅助

    private func loadBook() {
        book = locator.bookRepository.fetchBook(byId: bookId)
    }

    private func statusLabel(_ status: ReadingStatus) -> String {
        switch status {
        case .unread: return "未读"
        case .reading: return "在读"
        case .finished: return "已读完"
        case .wanted: return "想读"
        }
    }

    private func statusColor(_ status: ReadingStatus) -> Color {
        switch status {
        case .unread: return .gray
        case .reading: return DesignTokens.accent
        case .finished: return DesignTokens.success
        case .wanted: return .blue
        }
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
}
