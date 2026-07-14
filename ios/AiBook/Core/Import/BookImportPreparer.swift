import CryptoKit
import Foundation

struct PreparedBookImport: Sendable {
    let sourceURL: URL
    let fileName: String
    let format: BookFormat
    let data: Data
    let sha256: String
    let normalizedTitle: String
}

enum PreparedBookImportResult: Sendable {
    case prepared(PreparedBookImport)
    case unsupported
    case failed
}

actor BookImportPreparer {
    func prepare(url: URL, fileName: String) -> PreparedBookImportResult {
        guard let format = BookFormat.fromFileName(fileName) else {
            return .unsupported
        }

        let hasSecurityScope = url.startAccessingSecurityScopedResource()
        defer {
            if hasSecurityScope {
                url.stopAccessingSecurityScopedResource()
            }
        }

        guard let data = try? Data(contentsOf: url) else {
            return .failed
        }

        let sha256 = SHA256.hash(data: data)
            .map { String(format: "%02x", $0) }
            .joined()

        return .prepared(PreparedBookImport(
            sourceURL: url,
            fileName: fileName,
            format: format,
            data: data,
            sha256: sha256,
            normalizedTitle: ImportPolicy.normalizedTitle(from: fileName)
        ))
    }
}
