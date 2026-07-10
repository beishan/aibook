import SwiftUI
import UIKit
import CoreGraphics
import CoreText

// MARK: - CustomFontImporter（自定义字体导入，与安卓 ReaderFontType.CUSTOM 对应）

final class CustomFontImporter {

    struct ImportedFont: Identifiable {
        let id: String
        let name: String
        let fileName: String
        let filePath: String
    }

    /// 导入字体文件到 App 私有目录
    static func importFont(from sourceUrl: URL) -> Result<ImportedFont, FontImportError> {
        let fileName = sourceUrl.lastPathComponent

        // 校验文件格式
        guard ReaderFontCatalog.isSupportedFontFile(fileName) else {
            return .failure(.unsupportedFormat)
        }

        // 读取文件
        guard let data = try? Data(contentsOf: sourceUrl) else {
            return .failure(.readFailed)
        }

        // 复制到私有目录
        let fontId = UUID().uuidString
        let fontsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("reader_fonts", isDirectory: true)
        try? FileManager.default.createDirectory(at: fontsDir, withIntermediateDirectories: true)
        let destUrl = fontsDir.appendingPathComponent("\(fontId).\((fileName as NSString).pathExtension)")

        do {
            try data.write(to: destUrl)
        } catch {
            return .failure(.writeFailed)
        }

        // 注册字体
        guard let fontName = registerFont(at: destUrl) else {
            try? FileManager.default.removeItem(at: destUrl)
            return .failure(.registrationFailed)
        }

        return .success(ImportedFont(
            id: fontId,
            name: fontName,
            fileName: fileName,
            filePath: destUrl.path
        ))
    }

    /// 获取已导入的字体列表
    static func importedFonts() -> [ImportedFont] {
        let fontsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("reader_fonts", isDirectory: true)

        guard let files = try? FileManager.default.contentsOfDirectory(
            at: fontsDir,
            includingPropertiesForKeys: nil
        ) else { return [] }

        return files.compactMap { url in
            let fileName = url.lastPathComponent
            let fontId = url.deletingPathExtension().lastPathComponent
            guard ReaderFontCatalog.isSupportedFontFile(fileName) else { return nil }
            return ImportedFont(
                id: fontId,
                name: fileName,
                fileName: fileName,
                filePath: url.path
            )
        }
    }

    /// 删除已导入的字体
    static func deleteFont(_ font: ImportedFont) {
        try? FileManager.default.removeItem(atPath: font.filePath)
    }

    // MARK: - 字体注册

    private static func registerFont(at url: URL) -> String? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        guard let provider = CGDataProvider(data: data as CFData) else { return nil }
        guard let cgFont = CGFont(provider) else { return nil }

        var errorRef: Unmanaged<CFError>?
        let success = CTFontManagerRegisterGraphicsFont(cgFont, &errorRef)

        if !success {
            return nil
        }

        return cgFont.postScriptName as String?
    }
}

enum FontImportError: LocalizedError {
    case unsupportedFormat
    case readFailed
    case writeFailed
    case registrationFailed

    var errorDescription: String? {
        switch self {
        case .unsupportedFormat: return "不支持的字体格式（仅支持 .ttf/.otf）"
        case .readFailed: return "字体文件读取失败"
        case .writeFailed: return "字体文件保存失败"
        case .registrationFailed: return "字体注册失败，文件可能已损坏"
        }
    }
}
