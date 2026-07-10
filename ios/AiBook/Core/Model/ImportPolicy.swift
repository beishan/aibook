import Foundation

// MARK: - ImportPolicy（与安卓 ImportPolicy.kt 对齐）

enum ImportPolicy {
    /// 支持的文件格式
    static let supportedFormats: Set<BookFormat> = [.epub, .txt, .pdf, .markdown, .html, .htm]

    /// 校验文件是否支持
    static func isSupported(fileName: String) -> Bool {
        BookFormat.fromFileName(fileName) != nil
    }

    /// 校验文件格式是否可阅读（排除 PDF，暂不支持）
    static func isReadable(format: BookFormat) -> Bool {
        format != .pdf
    }

    /// 从文件名标准化标题（去除扩展名）
    static func normalizedTitle(from fileName: String) -> String {
        let title = (fileName as NSString).deletingPathExtension
            .trimmingCharacters(in: .whitespaces)
        return title.isEmpty ? fileName : title
    }
}
