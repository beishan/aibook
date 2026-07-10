import Foundation

// MARK: - OpdsFeedParser（与安卓 OpdsFeedParser 对齐，使用 Foundation XMLParser SAX 模式）

final class OpdsFeedParser: NSObject, XMLParserDelegate {
    private var feed: OpdsFeed?
    private var entries: [OpdsEntry] = []
    private var currentEntry: OpdsEntryBuilder?
    private var currentElement: String = ""
    private var currentText: String = ""
    private var feedTitle: String?

    func parse(data: Data) -> OpdsFeed? {
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.shouldResolveExternalEntities = false

        guard parser.parse() else { return nil }

        return OpdsFeed(title: feedTitle, entries: entries)
    }

    // MARK: - XMLParserDelegate

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName: String?, attributes attributeDict: [String: String] = [:]) {
        currentElement = elementName
        currentText = ""

        switch elementName {
        case "entry":
            currentEntry = OpdsEntryBuilder()
        case "link":
            if var entry = currentEntry {
                let href = attributeDict["href"] ?? ""
                let rel = attributeDict["rel"] ?? ""
                let type = attributeDict["type"]
                let link = OpdsLink(href: href, type: type, rel: rel)

                if rel.contains("http://opds-spec.org/acquisition") {
                    entry.acquisitionLink = link
                } else if rel == "alternate" {
                    entry.alternateLink = link
                } else if rel.contains("http://opds-spec.org/image") {
                    entry.coverLink = link
                }
                currentEntry = entry
            }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentText += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName: String?) {
        let text = currentText.trimmingCharacters(in: .whitespacesAndNewlines)

        switch elementName {
        case "entry":
            if let entry = currentEntry {
                entries.append(entry.build())
            }
            currentEntry = nil
        case "title":
            if currentEntry != nil {
                currentEntry?.title = text
            } else if feedTitle == nil {
                feedTitle = text
            }
        case "name":
            if currentEntry?.author == nil {
                currentEntry?.author = text
            }
        case "summary":
            currentEntry?.summary = text
        default:
            break
        }

        currentText = ""
    }
}

// MARK: - OpdsEntryBuilder（可变构建器）

private struct OpdsEntryBuilder {
    var title: String = ""
    var author: String?
    var summary: String?
    var acquisitionLink: OpdsLink?
    var alternateLink: OpdsLink?
    var coverLink: OpdsLink?

    func build() -> OpdsEntry {
        OpdsEntry(
            title: title,
            author: author,
            summary: summary,
            acquisitionLink: acquisitionLink,
            alternateLink: alternateLink,
            coverLink: coverLink
        )
    }
}
