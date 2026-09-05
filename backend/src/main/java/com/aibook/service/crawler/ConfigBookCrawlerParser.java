package com.aibook.service.crawler;

import com.aibook.model.entity.CrawlerSite;
import com.aibook.model.entity.CrawlerSiteRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;

@Component("configBookCrawlerParser")
@RequiredArgsConstructor
public class ConfigBookCrawlerParser implements BookCrawlerParser {
    private final ObjectMapper objectMapper;

    @Override
    public List<ParsedDiscovery> parseBookList(String html, String pageUrl, CrawlerSiteRule rule) {
        Document doc = Jsoup.parse(html, pageUrl);
        String itemSelector = required(rule.getDiscoveryItemSelector(), "书籍发现项");
        List<ParsedDiscovery> result = new ArrayList<>();
        for (Element item : doc.select(itemSelector)) {
            Element link = select(item, rule.getDiscoveryUrlSelector());
            if (link == null) continue;
            String url = link.absUrl("href");
            if (url.isBlank()) url = resolve(pageUrl, link.attr("href"));
            if (url.isBlank()) continue;
            String title = scopedText(item, rule.getDiscoveryTitleSelector());
            if (title.isBlank()) title = link.text().trim();
            if (title.isBlank()) continue;
            result.add(new ParsedDiscovery(externalId(url), title,
                    scopedText(item, rule.getDiscoveryAuthorSelector()),
                    scopedAttr(item, rule.getDiscoveryCoverSelector(), "src", pageUrl),
                    scopedText(item, rule.getDiscoveryCategorySelector()),
                    scopedText(item, rule.getDiscoveryLatestChapterSelector()), url));
        }
        return result;
    }

    @Override
    public String parseNextBookListPage(String html, String pageUrl, CrawlerSiteRule rule) {
        if (rule.getDiscoveryNextPageSelector() == null || rule.getDiscoveryNextPageSelector().isBlank()) return "";
        Document doc = Jsoup.parse(html, pageUrl);
        return absoluteAttr(doc, rule.getDiscoveryNextPageSelector(), "href");
    }

    @Override
    public ParsedBook parseBookDetail(String html, String pageUrl, CrawlerSiteRule rule) {
        Document doc = Jsoup.parse(html, pageUrl);
        String title = text(doc, rule.getTitleSelector());
        if (title.isBlank()) throw new IllegalArgumentException("书名解析结果为空，请检查书名 Selector");
        return new ParsedBook(externalId(pageUrl), title,
                text(doc, rule.getAuthorSelector()), absoluteAttr(doc, rule.getCoverSelector(), "src"),
                text(doc, rule.getDescriptionSelector()), text(doc, rule.getCategorySelector()),
                text(doc, rule.getStatusSelector()), text(doc, rule.getLatestChapterSelector()),
                firstNonBlank(absoluteAttr(doc, rule.getChapterListUrlSelector(), "href"), pageUrl));
    }

    @Override
    public List<ParsedChapter> parseChapterList(String html, String pageUrl, CrawlerSiteRule rule) {
        Document doc = Jsoup.parse(html, pageUrl);
        List<ParsedChapter> result = new ArrayList<>();
        int index = 0;
        for (Element item : doc.select(required(rule.getChapterItemSelector(), "章节列表"))) {
            Element link = select(item, rule.getChapterUrlSelector());
            if (link == null) continue;
            String url = link.absUrl("href");
            if (url.isBlank()) url = resolve(pageUrl, link.attr("href"));
            if (url.isBlank()) continue;
            String title = scopedText(item, rule.getChapterTitleSelector());
            if (title.isBlank()) title = link.text().trim();
            if (title.isBlank()) title = "第 " + (index + 1) + " 章";
            result.add(new ParsedChapter(externalId(url), index++, title, url));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("章节目录解析结果为空，请检查章节 Selector");
        return result;
    }

    @Override
    public ParsedContent parseChapter(String html, String pageUrl, CrawlerSiteRule rule) {
        Document doc = Jsoup.parse(html, pageUrl);
        Element body = doc.selectFirst(required(rule.getContentSelector(), "正文"));
        if (body == null) throw new IllegalArgumentException("正文解析结果为空，请检查正文 Selector");
        for (String selector : lines(rule.getRemoveSelectors())) body.select(selector).remove();
        body.select("script,style,noscript,nav").remove();
        body.select("br").after("\n");
        body.select("p,div,li").append("\n");
        String content = Jsoup.parse(body.html()).wholeText();
        try {
            if (rule.getRegexReplacementsJson() != null && !rule.getRegexReplacementsJson().isBlank()) {
                Map<String, String> replacements = objectMapper.readValue(
                        rule.getRegexReplacementsJson(), new TypeReference<LinkedHashMap<String, String>>() { });
                for (var entry : replacements.entrySet()) content = content.replaceAll(entry.getKey(), entry.getValue());
            }
        } catch (Exception exception) {
            throw new IllegalArgumentException("正文正则替换配置不是有效 JSON 或正则表达式", exception);
        }
        content = content.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("(?m)^[ \\t]+|[ \\t]+$", "").replaceAll("\n{3,}", "\n\n").trim();
        return new ParsedContent(text(doc, rule.getContentTitleSelector()), content);
    }

    @Override
    public boolean supports(CrawlerSite site) { return site.getParserType() == CrawlerSite.ParserType.CONFIG; }

    private String text(Document doc, String selector) {
        Element element = select(doc, selector);
        return element == null ? "" : element.text().trim();
    }

    private String scopedText(Element root, String selector) {
        if (selector == null || selector.isBlank()) return root.text().trim();
        Element element = select(root, selector);
        return element == null ? "" : element.text().trim();
    }

    private String absoluteAttr(Document doc, String selector, String defaultAttr) {
        if (selector == null || selector.isBlank()) return "";
        String[] parts = selector.split("::", 2);
        Element element = doc.selectFirst(parts[0].trim());
        if (element == null) return "";
        String attr = parts.length == 2 ? parts[1].trim() : defaultAttr;
        String absolute = element.absUrl(attr);
        return absolute.isBlank() ? element.attr(attr).trim() : absolute;
    }

    private String scopedAttr(Element root, String selector, String defaultAttr, String pageUrl) {
        if (selector == null || selector.isBlank()) return "";
        String[] parts = selector.split("::", 2);
        Element element = select(root, parts[0].trim());
        if (element == null) return "";
        String attr = parts.length == 2 ? parts[1].trim() : defaultAttr;
        String value = element.absUrl(attr);
        return value.isBlank() ? resolve(pageUrl, element.attr(attr)) : value;
    }

    private Element select(Element root, String selector) {
        if (selector == null || selector.isBlank() || ":scope".equals(selector.trim())) return root;
        return root.selectFirst(selector.trim());
    }

    private String externalId(String url) {
        try {
            String path = URI.create(url).getPath().replaceAll("/+$", "");
            String value = path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.[^.]+$", "");
            return value.isBlank() ? Integer.toHexString(url.hashCode()) : value;
        } catch (Exception ignored) { return Integer.toHexString(url.hashCode()); }
    }

    private String resolve(String base, String relative) {
        try { return URI.create(base).resolve(relative).toString(); } catch (Exception ignored) { return ""; }
    }

    private List<String> lines(String value) {
        return value == null ? List.of() : Arrays.stream(value.split("[\\r\\n,]+"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " Selector 未配置");
        return value;
    }

    private String firstNonBlank(String first, String fallback) { return first == null || first.isBlank() ? fallback : first; }
}
