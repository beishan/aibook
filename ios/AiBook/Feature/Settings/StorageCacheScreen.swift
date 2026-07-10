import SwiftUI

// MARK: - StorageCacheScreen（与安卓 StorageCacheScreen.kt 对齐）

struct StorageCacheScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var storageUsed: String = "计算中..."
    @State private var bookCount: Int = 0

    var body: some View {
        List {
            Section("存储使用") {
                HStack {
                    Text("书籍文件")
                    Spacer()
                    Text(storageUsed)
                        .foregroundColor(DesignTokens.softText)
                }
                HStack {
                    Text("书籍数量")
                    Spacer()
                    Text("\(bookCount) 本")
                        .foregroundColor(DesignTokens.softText)
                }
            }

            Section {
                Button(role: .destructive) {
                    // TODO: 清理缓存
                } label: {
                    Label("清理缓存", systemImage: "trash")
                }
            }
        }
        .navigationTitle("存储缓存")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            bookCount = locator.bookRepository.fetchAllBooks().count
            let docsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            if let size = FileManager.default.directorySize(at: docsDir) {
                storageUsed = ByteCountFormatter.string(fromByteCount: Int64(size), countStyle: .file)
            }
        }
    }
}

private extension FileManager {
    func directorySize(at url: URL) -> UInt64? {
        guard let enumerator = enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey]) else { return nil }
        var total: UInt64 = 0
        for case let fileURL as URL in enumerator {
            let attrs = try? fileURL.resourceValues(forKeys: [.fileSizeKey])
            total += UInt64(attrs?.fileSize ?? 0)
        }
        return total
    }
}
