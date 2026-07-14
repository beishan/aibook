import Foundation
import Compression

// MARK: - EpubParser（轻量级 EPUB 解析器，提取元数据和章节内容）

final class EpubParser {

    struct EpubMetadata {
        let title: String?
        let author: String?
        let coverData: Data?
        let chapters: [EpubChapter]
    }

    struct EpubChapter {
        let title: String?
        let href: String
        let content: String
    }

    /// 解析 EPUB 文件，提取元数据（书名、作者、封面）和纯文本内容
    static func parse(url: URL) throws -> EpubMetadata {
        // EPUB 是 ZIP 文件
        let coordinator = try EpubCoordinator(data: Data(contentsOf: url))
        return try coordinator.extract()
    }
}

// MARK: - EpubCoordinator（EPUB 内部结构解析）

private final class EpubCoordinator {
    private let archive: EpubArchive

    init(data: Data) throws {
        self.archive = try EpubArchive(data: data)
    }

    func extract() throws -> EpubParser.EpubMetadata {
        // 1. 读取 container.xml 找到 OPF 路径
        let containerXml = try archive.readEntry(named: "META-INF/container.xml")
            ?? archive.readEntry(path: "META-INF/container.xml")
        guard let containerData = containerXml else {
            throw EpubParseError.containerNotFound
        }
        let opfPath = try parseContainerXml(containerData)

        // 2. 读取 OPF 文件
        let opfData = try archive.readEntry(path: opfPath)
        guard let opf = opfData else {
            throw EpubParseError.opfNotFound
        }

        let opfDir = (opfPath as NSString).deletingLastPathComponent

        // 3. 解析 OPF 提取 metadata 和 spine
        let opfResult = try parseOpf(opfData: opf, opfDir: opfDir)

        // 4. 提取封面图片
        var coverData: Data?
        if let coverHref = opfResult.coverHref {
            let fullPath = opfDir.isEmpty ? coverHref : "\(opfDir)/\(coverHref)"
            coverData = try archive.readEntry(path: fullPath)
        }

        // 5. 提取章节内容（按 spine 顺序）
        var chapters: [EpubParser.EpubChapter] = []
        for (index, item) in opfResult.spineItems.enumerated() {
            let fullPath = opfDir.isEmpty ? item.href : "\(opfDir)/\(item.href)"
            guard let htmlData = try archive.readEntry(path: fullPath),
                  let html = String(data: htmlData, encoding: .utf8) else { continue }

            let text = stripHtml(html)
            if !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                chapters.append(EpubParser.EpubChapter(
                    title: item.title ?? "第 \(index + 1) 章",
                    href: item.href,
                    content: text
                ))
            }
        }

        return EpubParser.EpubMetadata(
            title: opfResult.title,
            author: opfResult.author,
            coverData: coverData,
            chapters: chapters
        )
    }

    // MARK: - container.xml 解析

    private func parseContainerXml(_ data: Data) throws -> String {
        let parser = SimpleXmlParser(data: data)
        guard let rootFilePath = parser.findAttribute(
            element: "rootfile",
            attribute: "full-path"
        ) else {
            throw EpubParseError.rootfileNotFound
        }
        return rootFilePath
    }

    // MARK: - OPF 解析

    private struct OpfResult {
        let title: String?
        let author: String?
        let coverHref: String?
        let spineItems: [(href: String, title: String?)]
    }

    private func parseOpf(opfData: Data, opfDir: String) throws -> OpfResult {
        let parser = SimpleXmlParser(data: opfData)

        // metadata
        let title = parser.findText(in: "dc:title") ?? parser.findText(in: "title")
        let author = parser.findText(in: "dc:creator") ?? parser.findText(in: "creator")

        // cover image
        var coverHref: String?
        // 先找 meta name="cover" content="cover-id"
        if let coverId = parser.findAttribute(element: "meta", attribute: "value",
                                               whereAttribute: "name", equals: "cover") {
            // 在 manifest 中找这个 id 对应的 href
            coverHref = parser.findAttribute(element: "item", attribute: "href",
                                              whereAttribute: "id", equals: coverId)
        }
        // fallback: 找 rel="cover-image" 的 link
        if coverHref == nil {
            coverHref = parser.findAttribute(element: "link", attribute: "href",
                                              whereAttribute: "rel", contains: "cover")
        }
        // fallback: manifest 中 id 包含 "cover" 的 image item
        if coverHref == nil {
            coverHref = parser.findAttribute(element: "item", attribute: "href",
                                              whereAttribute: "id", contains: "cover")
        }

        // spine items (按顺序)
        let spineIds = parser.findOrderedAttributes(
            parentElement: "spine",
            childElement: "itemref",
            attribute: "idref"
        )

        // manifest: id -> (href, media-type)
        let manifestItems = parser.findAllAttributes(
            element: "item",
            attributes: ["id", "href", "media-type"]
        )

        var manifestMap: [String: (href: String, mediaType: String)] = [:]
        for attrs in manifestItems {
            if let id = attrs["id"], let href = attrs["href"] {
                manifestMap[id] = (href, attrs["media-type"] ?? "")
            }
        }

        // 构建 spine items
        var spineItems: [(href: String, title: String?)] = []
        for id in spineIds {
            guard let item = manifestMap[id] else { continue }
            // 只处理 XHTML 内容
            let mt = item.mediaType.lowercased()
            if mt.contains("html") || mt.contains("xhtml") || item.href.hasSuffix(".html") || item.href.hasSuffix(".xhtml") {
                spineItems.append((href: item.href, title: nil))
            }
        }

        return OpfResult(
            title: title,
            author: author,
            coverHref: coverHref,
            spineItems: spineItems
        )
    }

    // MARK: - HTML 转纯文本

    private func stripHtml(_ html: String) -> String {
        // 简单的 HTML 标签移除
        var text = html
        // 移除 script / style
        text = text.replacingOccurrences(of: "<(script|style)[^>]*>[\\s\\S]*?</\\1>", with: "", options: .regularExpression)
        // <br> / <p> 转换行
        text = text.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
        text = text.replacingOccurrences(of: "</p>", with: "\n\n", options: .regularExpression)
        text = text.replacingOccurrences(of: "</div>", with: "\n", options: .regularExpression)
        text = text.replacingOccurrences(of: "</h[1-6]>", with: "\n\n", options: .regularExpression)
        // 移除所有 HTML 标签
        text = text.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        // HTML 实体解码
        text = text.replacingOccurrences(of: "&amp;", with: "&")
        text = text.replacingOccurrences(of: "&lt;", with: "<")
        text = text.replacingOccurrences(of: "&gt;", with: ">")
        text = text.replacingOccurrences(of: "&quot;", with: "\"")
        text = text.replacingOccurrences(of: "&#39;", with: "'")
        text = text.replacingOccurrences(of: "&nbsp;", with: " ")
        // 合并多余空行
        text = text.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression)
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

// MARK: - SimpleXmlParser（轻量 XML 解析）

private final class SimpleXmlParser: NSObject, XMLParserDelegate {
    private var elements: [(name: String, attributes: [String: String])] = []
    private var currentText = ""
    private var textMap: [String: String] = [:]  // elementName -> accumulated text
    private var allElements: [(name: String, attributes: [String: String])] = []

    init(data: Data) {
        super.init()
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.shouldResolveExternalEntities = false
        parser.parse()
    }

    func findAttribute(element: String, attribute: String) -> String? {
        allElements.first { $0.name == element }?.attributes[attribute]
    }

    func findAttribute(element: String, attribute: String,
                       whereAttribute wa: String, equals value: String) -> String? {
        allElements.first { $0.name == element && $0.attributes[wa] == value }?.attributes[attribute]
    }

    func findAttribute(element: String, attribute: String,
                       whereAttribute wa: String, contains value: String) -> String? {
        allElements.first { $0.name == element && ($0.attributes[wa]?.contains(value) ?? false) }?.attributes[attribute]
    }

    func findText(in element: String) -> String? {
        textMap[element]?.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func findOrderedAttributes(parentElement: String, childElement: String, attribute: String) -> [String] {
        // 需要找到 parentElement 下的所有 childElement
        var result: [String] = []
        var insideParent = false
        for el in allElements {
            if el.name == parentElement { insideParent = true; continue }
            if insideParent && el.name == childElement {
                if let val = el.attributes[attribute] { result.append(val) }
            }
            // 简单处理：遇到闭合标签时退出（不完美但够用）
        }
        return result
    }

    func findAllAttributes(element: String, attributes: [String]) -> [[String: String]] {
        allElements.filter { $0.name == element }.map { el in
            var result: [String: String] = [:]
            for attr in attributes {
                result[attr] = el.attributes[attr]
            }
            return result
        }
    }

    // MARK: - XMLParserDelegate

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName: String?, attributes attributeDict: [String: String] = [:]) {
        // 去掉命名空间前缀
        let localName = elementName.components(separatedBy: ":").last ?? elementName
        allElements.append((name: localName, attributes: attributeDict))
        currentText = ""
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentText += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName: String?) {
        let localName = elementName.components(separatedBy: ":").last ?? elementName
        if !currentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if textMap[localName] == nil {
                textMap[localName] = currentText
            }
        }
    }
}

// MARK: - EpubArchive（ZIP 归档读取）

private final class EpubArchive {
    private let data: Data
    private var entries: [String: Range<Data.Index>] = [:]

    init(data: Data) throws {
        self.data = data
        try parseCentralDirectory()
    }

    func readEntry(path: String) throws -> Data? {
        let normalizedPath = path.replacingOccurrences(of: "\\", with: "/")
        guard let range = entries[normalizedPath] else { return nil }

        // 读取 local file header 获取压缩方式
        // 简化处理：假设条目在 central directory 中能找到
        // 需要从 local header 解压
        return try decompressEntry(at: range, path: normalizedPath)
    }

    func readEntry(named name: String) throws -> Data? {
        // 尝试不同路径格式
        if let data = try readEntry(path: name) { return data }
        // 尝试去掉前导 /
        if name.hasPrefix("/") {
            return try readEntry(path: String(name.dropFirst()))
        }
        return nil
    }

    // MARK: - ZIP 中央目录解析

    private func parseCentralDirectory() throws {
        // 查找 End of Central Directory 签名
        let eocdSignature: UInt32 = 0x06054b50
        var eocdOffset = data.count - 22

        while eocdOffset >= 0 {
            let sig = data[eocdOffset..<eocdOffset+4].withUnsafeBytes { $0.load(as: UInt32.self) }
            if sig == eocdSignature { break }
            eocdOffset -= 1
        }

        guard eocdOffset >= 0 else { throw EpubParseError.invalidZip }

        let centralDirOffset = Int(data[eocdOffset+16..<eocdOffset+20].withUnsafeBytes { $0.load(as: UInt32.self) })
        let numEntries = Int(data[eocdOffset+10..<eocdOffset+12].withUnsafeBytes { $0.load(as: UInt16.self) })

        // 遍历 central directory entries
        var offset = centralDirOffset
        let cdSignature: UInt32 = 0x02014b50

        for _ in 0..<numEntries {
            guard offset + 46 <= data.count else { break }
            let sig = data[offset..<offset+4].withUnsafeBytes { $0.load(as: UInt32.self) }
            guard sig == cdSignature else { break }

            let compressionMethod = data[offset+10..<offset+12].withUnsafeBytes { $0.load(as: UInt16.self) }
            let compressedSize = Int(data[offset+20..<offset+24].withUnsafeBytes { $0.load(as: UInt32.self) })
            let fileNameLength = Int(data[offset+28..<offset+30].withUnsafeBytes { $0.load(as: UInt16.self) })
            let extraFieldLength = Int(data[offset+30..<offset+32].withUnsafeBytes { $0.load(as: UInt16.self) })
            let commentLength = Int(data[offset+32..<offset+34].withUnsafeBytes { $0.load(as: UInt16.self) })
            let localHeaderOffset = Int(data[offset+42..<offset+46].withUnsafeBytes { $0.load(as: UInt32.self) })

            let fileNameStart = offset + 46
            let fileNameEnd = fileNameStart + fileNameLength
            guard fileNameEnd <= data.count else { break }

            if let fileName = String(data: data[fileNameStart..<fileNameEnd], encoding: .utf8) {
                // 计算 local file header 中数据的实际位置
                let dataOffset = localHeaderOffset + 30 + fileNameLength + extraFieldLength
                let dataEnd = dataOffset + compressedSize
                if dataEnd <= data.count {
                    entries[fileName] = dataOffset..<dataEnd
                    // 保存压缩方式信息
                    compressionMethods[fileName] = compressionMethod
                }
            }

            offset = fileNameEnd + extraFieldLength + commentLength
        }
    }

    private var compressionMethods: [String: UInt16] = [:]

    private func decompressEntry(at range: Range<Data.Index>, path: String) throws -> Data? {
        let compressedData = data[range]
        let method = compressionMethods[path] ?? 0

        if method == 0 {
            // 存储（无压缩）
            return compressedData
        } else if method == 8 {
            // Deflate 压缩
            return try inflate(compressedData)
        } else {
            return nil
        }
    }

    private func inflate(_ data: Data) throws -> Data {
        // 使用 Compression framework 解压 deflate 数据
        let decompressedSize = data.count * 10 // 估算
        var decompressed = Data(count: decompressedSize)

        let result = data.withUnsafeBytes { srcPtr -> Int in
            decompressed.withUnsafeMutableBytes { dstPtr -> Int in
                guard let src = srcPtr.baseAddress, let dst = dstPtr.baseAddress else { return -1 }
                return compression_decode_buffer(
                    dst.assumingMemoryBound(to: UInt8.self),
                    decompressedSize,
                    src.assumingMemoryBound(to: UInt8.self),
                    data.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
        }

        guard result > 0 else { return data }
        return decompressed.prefix(result)
    }
}

// MARK: - Errors

enum EpubParseError: LocalizedError {
    case containerNotFound
    case opfNotFound
    case rootfileNotFound
    case invalidZip

    var errorDescription: String? {
        switch self {
        case .containerNotFound: return "EPUB: container.xml 未找到"
        case .opfNotFound: return "EPUB: OPF 文件未找到"
        case .rootfileNotFound: return "EPUB: rootfile 路径未找到"
        case .invalidZip: return "EPUB: 无效的 ZIP 格式"
        }
    }
}
