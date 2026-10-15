package com.aibook.service.scraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.web.client.RestClientResponseException;

/** 仅在试刮削作用域内收集诊断；不共享来源实例状态，也不向浏览器暴露原始响应或密钥。 */
public final class ScrapeDiagnostics implements AutoCloseable {
    private static final ThreadLocal<ScrapeDiagnostics> CURRENT = new ThreadLocal<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ScrapeDiagnostics previous;
    private final Consumer<String> logger;
    private final List<Failure> failures = new ArrayList<>();
    private String stage = "配置检查";
    private String endpoint = "";
    private Integer httpStatus;

    public enum Code {
        CONFIRMATION_REQUIRED("候选需要人工确认", "请在试刮削中核对并选择候选书籍。", 15),
        NO_MATCH("未搜索到匹配书籍", "尝试核对 ISBN、书名或更换来源。", 0),
        UNKNOWN("原因未确认", "检查来源配置或稍后重试；不能据此断定配置可用。", 10),
        UNRECOGNIZED_RESPONSE("响应结构无法识别", "来源可能调整了响应结构，或返回了未识别的拦截页；请更换来源或检查解析规则。", 20),
        PARSE_ERROR("响应解析失败", "远端内容不符合预期格式，请更换来源或检查解析规则。", 30),
        HTTP_ERROR("来源返回 HTTP 错误", "检查状态码，稍后重试或更换来源。", 40),
        NETWORK_ERROR("网络连接失败", "检查 NAS 网络、代理配置及目标站点可达性。", 50),
        DNS_ERROR("域名解析失败", "检查 NAS 的 DNS 配置及外网连接。", 50),
        TLS_ERROR("TLS 连接失败", "检查服务器时间、证书信任及网络代理。", 50),
        TIMEOUT("请求超时", "检查 NAS 到来源的网络连接，稍后重试。", 50),
        ACCESS_DENIED("访问被拒绝（疑似反爬或权限限制）", "HTTP 403 不一定是反爬；检查来源访问要求或更换来源。", 60),
        AUTH_REQUIRED("来源要求登录或授权", "该来源返回登录/授权要求，请检查配置或更换来源。", 65),
        API_ERROR("来源 API 返回错误", "检查 API 配置及该服务的可用状态。", 65),
        API_KEY_INVALID("API Key 无效或权限不足", "核对已保存的 API Key 及 API 启用、授权范围。", 70),
        QUOTA_EXCEEDED("API 配额已耗尽", "检查来源账户配额，等待额度恢复或更换来源。", 75),
        RATE_LIMITED("请求受到限流", "降低请求频率，等待限制解除后重试。", 80),
        BLOCKED("检测到验证码或安全验证拦截", "来源返回安全验证页面，本次未取得正常搜索结果；稍后重试或更换来源。", 90),
        CONFIG_MISSING("缺少 API Key", "先保存有效的 Google Books API Key，再进行试刮削。", 100),
        UNSUPPORTED("来源不支持此书籍", "此来源仅支持包含中文的书名，可尝试其他来源。", 100);

        final String label;
        final String suggestion;
        final int priority;

        Code(String label, String suggestion, int priority) {
            this.label = label;
            this.suggestion = suggestion;
            this.priority = priority;
        }
    }

    public record Failure(Code code, String label, String detail, String suggestion,
            String stage, Integer httpStatus, String endpoint) {}

    private ScrapeDiagnostics(Consumer<String> logger) {
        this.logger = logger;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static ScrapeDiagnostics open(Consumer<String> logger) {
        return new ScrapeDiagnostics(logger);
    }

    public void stage(String value) {
        stage = value;
        endpoint = "";
        httpStatus = null;
    }

    public static void request(String url) {
        ScrapeDiagnostics current = CURRENT.get();
        if (current == null) return;
        current.endpoint = safeEndpoint(url);
        current.httpStatus = null;
        current.logger.accept(current.stage + "：请求 " + current.endpoint);
    }

    /** 识别 HTTP 错误，以及 HTTP 200 下的验证码/登录页和 API 错误信封。 */
    public static boolean response(int status, String finalUrl, String body) {
        ScrapeDiagnostics current = CURRENT.get();
        if (current != null) {
            current.httpStatus = status;
            current.endpoint = safeEndpoint(finalUrl);
            current.logger.accept(current.stage + "：HTTP " + status + " · " + current.endpoint);
        }
        Code apiError = apiError(body);
        if (apiError != null) {
            report(apiError, "来源 API 返回错误信封" + "（HTTP " + status + "）。");
            return false;
        }
        if (status == 429) {
            report(Code.RATE_LIMITED, "来源返回 HTTP 429，请求过于频繁。");
            return false;
        }
        if (status == 401) {
            report(Code.AUTH_REQUIRED, "来源返回 HTTP 401，需要有效授权。");
            return false;
        }
        Code pageError = pageError(finalUrl, body);
        if (pageError != null) {
            report(pageError, pageError == Code.BLOCKED
                    ? "响应包含验证码、安全验证标题或验证表单，未返回正常书籍页面。"
                    : "请求被重定向到登录入口，或响应包含登录页面。");
            return false;
        }
        if (status == 403) {
            report(Code.ACCESS_DENIED, "来源返回 HTTP 403；访问被拒绝，无法仅凭状态码确认是否为反爬。");
            return false;
        }
        if (status < 200 || status >= 300) {
            report(Code.HTTP_ERROR, "来源返回 HTTP " + status + "，未取得正常响应。");
            return false;
        }
        if (body == null || body.isBlank()) {
            report(Code.UNRECOGNIZED_RESPONSE, "来源返回空响应，不能确认是否有匹配书籍。");
            return false;
        }
        return true;
    }

    public static void exception(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                response(response.getStatusCode().value(), currentEndpoint(), response.getResponseBodyAsString());
                return;
            }
            Code code = cause instanceof SocketTimeoutException ? Code.TIMEOUT
                    : cause instanceof UnknownHostException ? Code.DNS_ERROR
                    : cause instanceof SSLException ? Code.TLS_ERROR
                    : cause instanceof ConnectException ? Code.NETWORK_ERROR
                    : cause instanceof JsonProcessingException ? Code.PARSE_ERROR : null;
            if (code != null) {
                report(code, code.label + "（" + cause.getClass().getSimpleName() + "）。");
                return;
            }
        }
        Code code = error instanceof org.springframework.web.client.ResourceAccessException
                || error instanceof java.io.IOException ? Code.NETWORK_ERROR : Code.UNKNOWN;
        report(code, "查询或解析未完成（" + error.getClass().getSimpleName() + "）。");
    }

    public static void note(String detail) {
        ScrapeDiagnostics current = CURRENT.get();
        if (current != null) current.logger.accept(current.stage + "：" + detail);
    }

    public static void report(Code code, String detail) {
        ScrapeDiagnostics current = CURRENT.get();
        if (current == null || current.failures.size() >= 100) return;
        Failure failure = new Failure(code, code.label, detail, code.suggestion,
                current.stage, current.httpStatus, current.endpoint);
        current.failures.add(failure);
        current.logger.accept(current.stage + "：" + code.label + "；" + detail);
    }

    public static void emptySearchPage(Document document) {
        String notices = document.select(".no-result, .result-none, .search-empty, .notice-filter-none, .m-error").text();
        String text = document.text();
        boolean explicitEmpty = (text.length() < 2000 ? text : notices)
                .matches("(?is).*(没有找到|未找到相关|没有搜索到|暂无相关|无相关商品|no results found).*");
        report(explicitEmpty ? Code.NO_MATCH : Code.UNRECOGNIZED_RESPONSE,
                explicitEmpty ? "来源搜索页明确提示没有匹配结果。"
                        : "搜索页未发现可解析的书籍链接，也没有明确的无结果提示，不能认定未搜索到。");
    }

    public List<Failure> failures() {
        return List.copyOf(failures);
    }

    public Failure primaryFailure() {
        if (failures.isEmpty()) report(Code.UNKNOWN, "来源未返回元信息，也没有提供可确认原因的诊断。");
        return failures.stream().max(Comparator.comparingInt(item -> item.code.priority)).orElseThrow();
    }

    private static String currentEndpoint() {
        return CURRENT.get() == null ? "" : CURRENT.get().endpoint;
    }

    static String safeEndpoint(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            // 仅展示已知端点路径；不返回查询参数、用户信息、片段或未知重定向路径。
            boolean known = path != null && path.matches(
                    "/(api/books|search\\.json|books/v1/volumes|search|Search|book/subject_search|j/subject_suggest|subject/\\d+/?|\\d+\\.html)");
            return uri.getHost() == null ? "" : uri.getScheme() + "://" + uri.getHost() + (known ? path : "/");
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static Code apiError(String body) {
        if (body == null || !body.stripLeading().startsWith("{")) return null;
        try {
            JsonNode root = JSON.readTree(body);
            if (!root.hasNonNull("error")) return null;
            JsonNode error = root.get("error");
            String reasons = error.path("status").asText();
            for (JsonNode entry : error.path("errors")) reasons += " " + entry.path("reason").asText();
            for (JsonNode entry : error.path("details")) reasons += " " + entry.path("reason").asText();
            reasons = reasons.toLowerCase(Locale.ROOT);
            if (reasons.contains("keyinvalid") || reasons.contains("api_key_invalid")
                    || reasons.contains("accessnotconfigured") || reasons.contains("iprefererblocked")) {
                return Code.API_KEY_INVALID;
            }
            if (reasons.contains("ratelimit") || reasons.contains("rate_limit")) return Code.RATE_LIMITED;
            if (reasons.contains("dailylimit") || reasons.contains("quota")
                    || reasons.contains("resource_exhausted")) return Code.QUOTA_EXCEEDED;
            return Code.API_ERROR;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Code pageError(String url, String body) {
        String location = url == null ? "" : url.toLowerCase(Locale.ROOT);
        // Do not inspect query strings: a book title may itself contain words such as captcha/login.
        try {
            URI uri = URI.create(location);
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.startsWith("passport.") || path.matches(".*/(login|signin|sign-in)(/.*)?")) {
                return Code.AUTH_REQUIRED;
            }
        } catch (IllegalArgumentException ignored) {
            // Body evidence can still identify a challenge when the final URL is unavailable.
        }
        if (body == null || !body.stripLeading().startsWith("<")) return null;
        Document document = Jsoup.parse(body);
        String title = document.title().toLowerCase(Locale.ROOT);
        boolean hasBookContent = !document.select(
                "h1 span, .sku-name, #info, #mainpic, a[href*='book.douban.com/subject/'], a[href*='item.jd.com/']").isEmpty();
        if (!hasBookContent && (title.matches(".*(验证码|安全验证|访问验证|访问受限|异常请求|captcha|security verification|access denied|just a moment|机器人验证).*")
                || !document.select("#captcha, input[name*=captcha], iframe[src*=captcha], #challenge-form, #JDJRV-wrap").isEmpty())) {
            return Code.BLOCKED;
        }
        if (title.matches(".*(登录|登陆|sign in|log in).*")
                && !document.select("input[type=password]").isEmpty()) return Code.AUTH_REQUIRED;
        return null;
    }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
