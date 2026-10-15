package com.aibook.service.scraper;

import com.aibook.model.entity.Book;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 豆瓣读书刮削器
 * 中文书籍信息最丰富的数据源
 */
@Component
@org.springframework.core.annotation.Order(1)
@Slf4j
public class DoubanScraper implements MetadataScraper {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    private static final String DOUBAN_SEARCH_URL = "https://search.douban.com/book/subject_search";
    private static final String DOUBAN_BOOK_URL = "https://book.douban.com/subject/";
    private static final String DOUBAN_SUGGEST_URL = "https://book.douban.com/j/subject_suggest";

    // 多个 User-Agent 轮换
    private static final String[] USER_AGENTS = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    public DoubanScraper() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String getName() {
        return "豆瓣读书";
    }

    @Override
    public String getConfigKey() {
        return "douban";
    }

    @Override
    public boolean supports(Book book) {
        // 中文书籍优先使用豆瓣
        String title = book.getTitle();
        if (title == null) return false;

        // 简单判断是否包含中文字符
        return title.matches(".*[\\u4e00-\\u9fa5].*");
    }

    @Override
    public BookMetadata scrape(Book book) {
        // 尝试通过 ISBN 查询
        if (book.getIsbn() != null && !book.getIsbn().isBlank()) {
            BookMetadata metadata = scrapeByIsbn(book.getIsbn());
            if (metadata != null) {
                return metadata;
            }
        }

        // 通过书名搜索
        return searchByTitle(book.getTitle());
    }

    @Override
    public BookMetadata scrapeByIsbn(String isbn) {
        try {
            // 添加随机延迟，避免请求过快
            addRandomDelay();

            String url = DOUBAN_SUGGEST_URL + "?q=" + isbn + "&type=isbn";
            log.info("查询豆瓣 ISBN: {}", isbn);

            String response = makeRequest(url);
            if (response == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode bookNode = root.get(0);
                String doubanId = bookNode.get("id").asText();
                BookMetadata metadata = getBookDetail(doubanId);
                if (metadata != null && metadata.getIsbn() != null
                        && metadata.getIsbn().replaceAll("[^0-9Xx]", "")
                                .equalsIgnoreCase(isbn.replaceAll("[^0-9Xx]", ""))) return metadata;
                ScrapeDiagnostics.report(ScrapeDiagnostics.Code.CONFIRMATION_REQUIRED,
                        "ISBN 查询的详情未能确认 ISBN 完全一致，请人工核对候选。");
                return null;
            }

            reportSuggestEmpty(root);
            log.warn("豆瓣未找到 ISBN: {}", isbn);
            return null;
        } catch (Exception e) {
            ScrapeDiagnostics.exception(e);
            log.error("豆瓣 ISBN 查询失败: {}", isbn, e);
            return null;
        }
    }

    /** 书名自动刮削只接受归一化后完全一致的候选；模糊结果交由用户确认。 */
    public BookMetadata searchByTitle(String title) {
        List<Candidate> candidates = searchCandidates(title);
        String expected = normalizedTitle(title);
        List<Candidate> exact = candidates.stream()
                .filter(candidate -> normalizedTitle(candidate.title()).equals(expected)).toList();
        if (exact.size() == 1) {
            BookMetadata metadata = getBookDetail(exact.get(0).id());
            if (metadata != null && normalizedTitle(metadata.getTitle()).equals(expected)) return metadata;
        }
        if (!candidates.isEmpty()) {
            ScrapeDiagnostics.report(ScrapeDiagnostics.Code.CONFIRMATION_REQUIRED,
                    "已找到备选书籍，但书名未能确认完全一致，请在试刮削中选择并人工确认。");
        }
        return null;
    }

    public record Candidate(String id, String title, String author, String publishDate,
            String coverUrl, String sourceUrl) {}

    /** 最多返回 8 个独立候选，不请求详情、不保存书籍。 */
    public List<Candidate> searchCandidates(String title) {
        String query = MetadataScraper.cleanSearchTitle(title);
        if (query == null || query.isBlank()) return List.of();
        try {
            addRandomDelay();
            ScrapeDiagnostics.note("通过豆瓣读书搜索页查询候选");
            String url = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(DOUBAN_SEARCH_URL)
                    .queryParam("search_text", query).queryParam("cat", "1001")
                    .build().encode().toUriString();
            String response = makeRequest(url);
            if (response != null) {
                List<Candidate> candidates = parseSearchCandidates(Jsoup.parse(response));
                if (!candidates.isEmpty()) return candidates;
            }
        } catch (Exception exception) {
            ScrapeDiagnostics.exception(exception);
        }
        try {
            addRandomDelay();
            ScrapeDiagnostics.note("搜索页未取得候选，尝试 suggest 备用接口");
            String url = org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(DOUBAN_SUGGEST_URL)
                    .queryParam("q", query).queryParam("type", "book")
                    .build().encode().toUriString();
            String response = makeRequest(url);
            return response == null ? List.of() : parseSuggestCandidates(objectMapper.readTree(response));
        } catch (Exception exception) {
            ScrapeDiagnostics.exception(exception);
            return List.of();
        }
    }

    List<Candidate> parseSuggestCandidates(JsonNode root) {
        if (root == null || !root.isArray()) {
            reportSuggestEmpty(root);
            return List.of();
        }
        java.util.LinkedHashMap<String, Candidate> candidates = new java.util.LinkedHashMap<>();
        for (JsonNode node : root) {
            String id = node.path("id").asText();
            String title = node.path("title").asText();
            if (!id.matches("\\d{1,20}") || title.isBlank()
                    || "movie".equals(node.path("type").asText())) continue;
            candidates.putIfAbsent(id, new Candidate(id, title, node.path("author_name").asText(),
                    node.path("year").asText(), node.path("pic").asText(), DOUBAN_BOOK_URL + id + "/"));
            if (candidates.size() == 8) break;
        }
        if (candidates.isEmpty()) reportSuggestEmpty(root);
        return List.copyOf(candidates.values());
    }

    List<Candidate> parseSearchCandidates(Document document) {
        // 当前搜索页由前端渲染，但候选已经以 JSON 嵌入 HTML；只读取数据，不执行页面脚本。
        Pattern assignment = Pattern.compile("(?:^|[;\\s])window\\.__DATA__\\s*=\\s*");
        for (Element script : document.select("script:not([src])")) {
            Matcher matcher = assignment.matcher(script.data());
            if (!matcher.find()) continue;
            try {
                JsonNode data = objectMapper.readTree(script.data().substring(matcher.end()));
                if (data == null || !data.isObject() || !data.path("items").isArray()
                        || !data.path("error_info").asText().isBlank()) {
                    ScrapeDiagnostics.report(ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE,
                            "豆瓣搜索页数据不是预期的 items 列表或包含来源错误，无法确认搜索结果。");
                    return List.of();
                }
                java.util.LinkedHashMap<String, Candidate> candidates = new java.util.LinkedHashMap<>();
                for (JsonNode item : data.get("items")) {
                    // 跳过丛书、搜索更多等非书籍条目，仅使用受校验的 subject 链接。
                    String id = subjectId(item.path("url").asText());
                    String title = item.path("title").asText();
                    if (id == null || title.isBlank()) continue;
                    String[] info = item.path("abstract").asText().split("\\s+/\\s+");
                    String date = "";
                    for (String part : info) {
                        if (part.matches("\\d{4}(?:-\\d{1,2}){0,2}")) date = part;
                    }
                    candidates.putIfAbsent(id, new Candidate(id, title, info.length == 0 ? "" : info[0],
                            date, item.path("cover_url").asText(), DOUBAN_BOOK_URL + id + "/"));
                    if (candidates.size() == 8) break;
                }
                if (candidates.isEmpty()) {
                    boolean empty = data.path("total").isNumber() && data.path("total").asInt() == 0;
                    ScrapeDiagnostics.report(empty ? ScrapeDiagnostics.Code.NO_MATCH
                                    : ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE,
                            empty ? "豆瓣读书搜索页明确返回 total=0，未找到匹配书籍。"
                                    : "搜索页未返回可识别的单本书籍条目，不能认定没有搜索结果。");
                }
                return List.copyOf(candidates.values());
            } catch (Exception exception) {
                ScrapeDiagnostics.exception(exception);
                return List.of();
            }
        }
        // 同时兼容已经渲染的新版结果和旧版 HTML 结果。
        java.util.LinkedHashMap<String, Candidate> candidates = new java.util.LinkedHashMap<>();
        for (Element link : document.select(".item-root a.title[href], .item-root .title a[href], "
                + ".result .title a[href], a[href*='book.douban.com/subject/']")) {
            String id = subjectId(link.attr("href"));
            String title = link.text().trim();
            if (id == null || title.isBlank()) continue;
            Element result = link.closest(".result, .item-root");
            Element info = result == null ? null : result.selectFirst(".subject-cast, .abstract");
            Element image = result == null ? null : result.selectFirst("img[src]");
            candidates.putIfAbsent(id, new Candidate(id, title, info == null ? "" : info.text(),
                    "", image == null ? "" : image.attr("src"), DOUBAN_BOOK_URL + id + "/"));
            if (candidates.size() == 8) break;
        }
        if (candidates.isEmpty()) ScrapeDiagnostics.emptySearchPage(document);
        return List.copyOf(candidates.values());
    }

    private String subjectId(String href) {
        try {
            java.net.URI uri = java.net.URI.create(href.startsWith("//") ? "https:" + href : href);
            if ("www.douban.com".equals(uri.getHost()) && "/link2/".equals(uri.getPath())) {
                String query = uri.getRawQuery();
                if (query == null) return null;
                for (String parameter : query.split("&")) {
                    if (parameter.startsWith("url=")) {
                        uri = java.net.URI.create(java.net.URLDecoder.decode(
                                parameter.substring(4), java.nio.charset.StandardCharsets.UTF_8));
                        break;
                    }
                }
            }
            if (!"book.douban.com".equals(uri.getHost())) return null;
            Matcher matcher = Pattern.compile("^/subject/(\\d{1,20})/?$").matcher(uri.getPath());
            return matcher.matches() ? matcher.group(1) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static String normalizedTitle(String title) {
        return title == null ? "" : title.replaceAll("[\\s\\p{P}]+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private void reportSuggestEmpty(JsonNode root) {
        ScrapeDiagnostics.report(root != null && root.isArray() && root.isEmpty()
                        ? ScrapeDiagnostics.Code.NO_MATCH : ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE,
                root != null && root.isArray() && root.isEmpty()
                        ? "豆瓣 suggest 接口返回空数组，未搜索到匹配书籍。"
                        : "豆瓣 suggest 响应结构不符合预期，不能确认是否有匹配书籍。");
    }

    /**
     * 发送 HTTP 请求
     */
    Connection createConnection(String url) {
        return Jsoup.connect(url);
    }

    private String makeRequest(String url) {
        try {
            ScrapeDiagnostics.request(url);
            String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];

            Connection.Response response = createConnection(url)
                    .userAgent(userAgent)
                    .referrer("https://book.douban.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .timeout(15000)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .execute();

            String body = response.body();
            return ScrapeDiagnostics.response(response.statusCode(), response.url().toString(), body)
                    ? body : null;
        } catch (Exception e) {
            ScrapeDiagnostics.exception(e);
            log.error("请求豆瓣失败: {}", url, e);
            return null;
        }
    }

    /**
     * 获取书籍详情（解析 HTML 页面）
     */
    public BookMetadata getBookDetail(String doubanId) {
        if (doubanId == null || !doubanId.matches("\\d{1,20}")) {
            throw new IllegalArgumentException("豆瓣候选编号无效");
        }
        try {
            addRandomDelay();

            String bookUrl = DOUBAN_BOOK_URL + doubanId + "/";
            log.info("获取豆瓣书籍详情: {}", bookUrl);

            String response = makeRequest(bookUrl);
            if (response == null) {
                return null;
            }

            Document doc = Jsoup.parse(response);
            return parseBookPage(doc, doubanId);
        } catch (Exception e) {
            ScrapeDiagnostics.exception(e);
            log.error("获取豆瓣书籍详情失败: {}", doubanId, e);
            return null;
        }
    }

    /**
     * 解析豆瓣书籍页面
     */
    private BookMetadata parseBookPage(Document doc, String doubanId) {
        try {
            BookMetadata.BookMetadataBuilder builder = BookMetadata.builder();

            // 标题
            Element titleElement = doc.selectFirst("h1 span");
            if (titleElement == null || titleElement.text().isBlank()) {
                ScrapeDiagnostics.report(ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE,
                        "详情页缺少预期的书名节点，无法确认页面为正常书籍详情；未生成仅含语言的伪结果。");
                return null;
            }
            if (titleElement != null) {
                builder.title(titleElement.text().trim());
            }

            // 作者信息（在 #info div 中）
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                // 解析作者
                String author = extractInfoField(infoElement, "作者");
                if (author != null) {
                    builder.author(author);
                }

                // 解析出版社
                String publisher = extractInfoField(infoElement, "出版社");
                if (publisher != null) {
                    builder.publisher(publisher);
                }

                // 解析出版年
                String publishDate = extractInfoField(infoElement, "出版年");
                if (publishDate != null) {
                    builder.publishDate(publishDate);
                }

                // 解析 ISBN
                String isbn = extractInfoField(infoElement, "ISBN");
                if (isbn != null) {
                    builder.isbn(isbn);
                }
            }

            // 评分
            Element ratingElement = doc.selectFirst("strong.rating_num");
            if (ratingElement != null) {
                try {
                    String ratingText = ratingElement.text().trim();
                    if (!ratingText.isEmpty()) {
                        double rating = Double.parseDouble(ratingText);
                        builder.rating(rating);
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }

            // 封面图片
            Element coverElement = doc.selectFirst("#mainpic img");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("src");
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    builder.coverUrl(coverUrl);
                }
            }

            // 内容简介
            Element summaryElement = doc.selectFirst("#link-report .all .intro");
            if (summaryElement == null) summaryElement = doc.selectFirst("#link-report .intro");
            if (summaryElement != null) {
                Elements paragraphs = summaryElement.select("p");
                StringBuilder summary = new StringBuilder();
                for (Element p : paragraphs) {
                    if (summary.length() > 0) {
                        summary.append("\n");
                    }
                    summary.append(p.text().trim());
                }
                if (summary.length() > 0) {
                    builder.description(summary.toString());
                }
            }

            // 标签
            Elements tagElements = doc.select("#db-tags-section .tag a");
            if (!tagElements.isEmpty()) {
                List<String> tags = new ArrayList<>();
                for (Element tag : tagElements) {
                    tags.add(tag.text().trim());
                }
                builder.tags(tags.toArray(new String[0]));
            }

            // 语言
            String title = builder.build().getTitle();
            if (title != null && title.matches(".*[\\u4e00-\\u9fa5].*")) {
                builder.language("zh");
            } else {
                builder.language("en");
            }

            return builder.build();
        } catch (Exception e) {
            ScrapeDiagnostics.exception(e);
            log.error("解析豆瓣页面失败", e);
            return null;
        }
    }

    /**
     * 从 info 区域提取字段值
     */
    private String extractInfoField(Element infoElement, String fieldName) {
        try {
            String html = infoElement.html();
            Pattern pattern = Pattern.compile(Pattern.quote(fieldName)
                    + "\\s*[:：]?\\s*</span>\\s*[:：]?\\s*(.*?)(?:<br|</div|$)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String value = matcher.group(1);
                value = Jsoup.parse(value).text().trim();
                value = value.replaceAll("^[:\\s]+", "");
                return value.isEmpty() ? null : value;
            }

            return null;
        } catch (Exception e) {
            log.debug("提取字段 {} 失败", fieldName, e);
            return null;
        }
    }

    /**
     * 添加随机延迟，避免请求过快
     */
    private void addRandomDelay() {
        try {
            // 随机延迟 1-3 秒
            int delay = 1000 + random.nextInt(2000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
