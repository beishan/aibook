import SwiftUI

// MARK: - BookCoverView（书籍封面通用组件 — 支持真实封面图片）

struct BookCoverView: View {
    let title: String
    let author: String?
    let format: BookFormat
    let coverUri: String?
    let width: CGFloat
    let height: CGFloat

    @State private var coverImage: UIImage?

    init(
        title: String,
        author: String? = nil,
        format: BookFormat = .epub,
        coverUri: String? = nil,
        width: CGFloat = 100,
        height: CGFloat = 140
    ) {
        self.title = title
        self.author = author
        self.format = format
        self.coverUri = coverUri
        self.width = width
        self.height = height
    }

    var body: some View {
        ZStack {
            if let image = coverImage {
                // 真实封面图片
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: width, height: height)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            } else {
                // 占位封面
                placeholderCover
            }
        }
        .frame(width: width, height: height)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
        .onAppear { loadCover() }
        .onChange(of: coverUri) { _, _ in loadCover() }
    }

    // MARK: - 占位封面

    private var placeholderCover: some View {
        ZStack {
            // 渐变背景
            RoundedRectangle(cornerRadius: 6)
                .fill(
                    LinearGradient(
                        colors: [DesignTokens.warmCard, DesignTokens.warmCard.opacity(0.8)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(DesignTokens.hairline, lineWidth: 0.5)
                )

            VStack(spacing: 6) {
                Spacer()

                Image(systemName: "book.closed")
                    .font(.system(size: width * 0.25))
                    .foregroundColor(DesignTokens.softText.opacity(0.35))

                Text(title)
                    .font(.system(size: min(12, width * 0.12), weight: .medium))
                    .foregroundColor(.primary.opacity(0.8))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 6)

                if let author = author, !author.isEmpty {
                    Text(author)
                        .font(.system(size: min(10, width * 0.1)))
                        .foregroundColor(DesignTokens.softText.opacity(0.7))
                        .lineLimit(1)
                        .padding(.horizontal, 6)
                }

                Spacer()
            }
            .padding(4)

            // 格式标识
            VStack {
                HStack {
                    Spacer()
                    Text(format.displayName)
                        .font(.system(size: max(7, width * 0.08), weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(
                            Capsule().fill(DesignTokens.softText.opacity(0.65))
                        )
                }
                Spacer()
            }
            .padding(5)
        }
    }

    // MARK: - 加载封面图片

    private func loadCover() {
        guard let path = coverUri, !path.isEmpty else {
            coverImage = nil
            return
        }

        let url = URL(fileURLWithPath: path)
        guard FileManager.default.fileExists(atPath: path),
              let data = try? Data(contentsOf: url),
              let image = UIImage(data: data) else {
            coverImage = nil
            return
        }

        coverImage = image
    }
}
