import Foundation
import CryptoKit

struct AudioListeningInterval: Codable, Sendable {
    var startMs: Int64
    var endMs: Int64
}
struct AudioOfflineProgress: Codable, Sendable {
    var eventId: String
    var baseRevision: Int64
    var contentRevision: Int64
    var trackId: Int64
    var positionMs: Int64
    var finished = false
    var resolveConflict = false
    var intervals: [AudioListeningInterval] = []
}

@MainActor
final class AudioOfflineCache {
    static let shared = AudioOfflineCache()
    private let manager = FileManager.default
    private let config = ServiceLocator.shared.serverConfigStore
    private let client = ServiceLocator.shared.apiClient

    private func root() throws -> URL {
        guard let server = config.serverUrl, let username = config.username, !username.isEmpty else { throw ApiError.unauthorized }
        let namespace = SHA256.hash(data: Data("\(server)|\(username)".utf8)).map { String(format: "%02x", $0) }.joined()
        let support = try manager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        var folder = support.appendingPathComponent("Audio/\(namespace)", isDirectory: true)
        try manager.createDirectory(at: folder, withIntermediateDirectories: true)
        var resource = URLResourceValues(); resource.isExcludedFromBackup = true
        try folder.setResourceValues(resource)
        return folder
    }
    private func folder(_ id: Int64) throws -> URL {
        let path = try root().appendingPathComponent(String(id), isDirectory: true)
        try manager.createDirectory(at: path, withIntermediateDirectories: true)
        return path
    }
    func list() throws -> [AudioEditionDTO] {
        try manager.contentsOfDirectory(at: root(), includingPropertiesForKeys: [.isDirectoryKey]).compactMap { directory in
            try? JSONDecoder().decode(AudioEditionDTO.self, from: Data(contentsOf: directory.appendingPathComponent("manifest.json")))
        }
    }
    func file(_ id: Int64, _ trackId: Int64) throws -> URL { try folder(id).appendingPathComponent("\(trackId).audio") }
    func progress(_ id: Int64) throws -> AudioOfflineProgress? {
        let path = try folder(id).appendingPathComponent("progress.json")
        guard manager.fileExists(atPath: path.path) else { return nil }
        return try JSONDecoder().decode(AudioOfflineProgress.self, from: Data(contentsOf: path))
    }
    func save(_ id: Int64, _ progress: AudioOfflineProgress) throws {
        try JSONEncoder().encode(progress).write(to: folder(id).appendingPathComponent("progress.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    func manifest(_ edition: AudioEditionDTO) throws {
        try JSONEncoder().encode(edition).write(to: folder(edition.id).appendingPathComponent("manifest.json"), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    func clearProgress(_ id: Int64) throws {
        let path = try folder(id).appendingPathComponent("progress.json")
        if manager.fileExists(atPath: path.path) { try manager.removeItem(at: path) }
    }
    func remove(_ id: Int64) throws {
        guard try progress(id) == nil else { throw AudioCacheError.message("存在未同步位置，请先同步后再删除") }
        try manager.removeItem(at: folder(id))
    }

    func download(_ id: Int64, notify: @MainActor (String) -> Void) async throws {
        let identity = "\(config.serverUrl ?? "")|\(config.username ?? "")"
        let edition: AudioEditionDTO = try await client.get("api/audiobooks/\(id)/offline-manifest")
        guard let tracks = edition.tracks, !tracks.isEmpty else { throw ApiError.notFound }
        let destination = try folder(id)
        let quota: Int64 = 5 * 1024 * 1024 * 1024
        let required = tracks.reduce(Int64(0)) { $0 + ($1.fileSize ?? 0) }
        guard required > 0 && required <= quota else { throw AudioCacheError.message("单本离线音频不能超过5GB") }
        let allFiles = manager.enumerator(at: try root(), includingPropertiesForKeys: [.fileSizeKey, .isRegularFileKey])
        var used: Int64 = 0
        while let file = allFiles?.nextObject() as? URL {
            let values = try file.resourceValues(forKeys: [.fileSizeKey, .isRegularFileKey])
            if values.isRegularFile == true { used += Int64(values.fileSize ?? 0) }
        }
        var remaining: Int64 = 0
        for track in tracks {
            let completed = destination.appendingPathComponent("\(track.id).audio")
            let partial = destination.appendingPathComponent("\(track.id).part")
            if manager.fileExists(atPath: completed.path), try await digest(completed) == track.fileHash { continue }
            remaining += max(0, (track.fileSize ?? 0) - size(partial))
        }
        guard used + remaining <= quota else { throw AudioCacheError.message("缓存限额5GB，请先清理离线文件") }
        let free = (try manager.attributesOfFileSystem(forPath: destination.path)[.systemFreeSize] as? NSNumber)?.int64Value ?? 0
        guard free > remaining + 100 * 1024 * 1024 else { throw AudioCacheError.message("设备空间不足") }
        for (index, track) in tracks.enumerated() {
            try Task.checkCancellation()
            guard identity == "\(config.serverUrl ?? "")|\(config.username ?? "")", let base = config.serverUrl else { throw ApiError.unauthorized }
            guard let expectedSize = track.fileSize, let expectedHash = track.fileHash else { throw ApiError.invalidResponse }
            let completed = destination.appendingPathComponent("\(track.id).audio")
            let partial = destination.appendingPathComponent("\(track.id).part")
            if manager.fileExists(atPath: completed.path), size(completed) == expectedSize, try await digest(completed) == expectedHash { continue }
            var offset = size(partial)
            if offset >= expectedSize {
                if offset == expectedSize, try await digest(partial) == expectedHash {
                    if manager.fileExists(atPath: completed.path) { try manager.removeItem(at: completed) }
                    try manager.moveItem(at: partial, to: completed); continue
                }
                try manager.removeItem(at: partial); offset = 0
            }
            guard let url = URL(string: "\(base.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/api/audiobooks/\(id)/tracks/\(track.id)/download") else { throw ApiError.invalidUrl }
            var request = URLRequest(url: url)
            if let token = config.token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
            if offset > 0 { request.setValue("bytes=\(offset)-", forHTTPHeaderField: "Range") }
            let (bytes, response) = try await URLSession.shared.bytes(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 || http.statusCode == 206 else { throw ApiError.invalidResponse }
            let append = http.statusCode == 206 && offset > 0
            if append && http.value(forHTTPHeaderField: "Content-Range")?.hasPrefix("bytes \(offset)-") != true { throw ApiError.invalidResponse }
            if !append || !manager.fileExists(atPath: partial.path) { manager.createFile(atPath: partial.path, contents: Data()) }
            try manager.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: partial.path)
            let handle = try FileHandle(forWritingTo: partial)
            do {
                if append { try handle.seekToEnd() }
                var buffer = Data(); buffer.reserveCapacity(65536)
                var written = append ? offset : 0
                for try await byte in bytes {
                    buffer.append(byte)
                    if buffer.count >= 65536 {
                        try Task.checkCancellation()
                        guard identity == "\(config.serverUrl ?? "")|\(config.username ?? "")" else { throw ApiError.unauthorized }
                        written += Int64(buffer.count)
                        guard written <= expectedSize else { throw ApiError.invalidResponse }
                        try handle.write(contentsOf: buffer); buffer.removeAll(keepingCapacity: true)
                    }
                }
                if !buffer.isEmpty { try handle.write(contentsOf: buffer) }
                try handle.close()
            } catch { try? handle.close(); throw error }
            guard size(partial) == expectedSize, try await digest(partial) == expectedHash else {
                try? manager.removeItem(at: partial)
                throw AudioCacheError.message("音轨校验失败，请重新下载")
            }
            if manager.fileExists(atPath: completed.path) { try manager.removeItem(at: completed) }
            try manager.moveItem(at: partial, to: completed)
            notify("已下载 \(index + 1)/\(tracks.count) 集")
        }
        guard identity == "\(config.serverUrl ?? "")|\(config.username ?? "")" else { throw ApiError.unauthorized }
        try manifest(edition)
    }
    private func size(_ path: URL) -> Int64 { (try? manager.attributesOfItem(atPath: path.path)[.size] as? NSNumber)?.int64Value ?? 0 }
    private func digest(_ path: URL) async throws -> String {
        try await Task.detached {
            let handle = try FileHandle(forReadingFrom: path)
            defer { try? handle.close() }
            var digest = SHA256()
            while let bytes = try handle.read(upToCount: 65536), !bytes.isEmpty { try Task.checkCancellation(); digest.update(data: bytes) }
            return digest.finalize().map { String(format: "%02x", $0) }.joined()
        }.value
    }
}

enum AudioCacheError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let value) = self { value } else { nil } }
}
