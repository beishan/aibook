package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ScrapeDiagnosticsTest {
    @Test
    void forbiddenIsSuspectedAccessRestrictionRatherThanConfirmedCaptcha() {
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.request("https://search.jd.com/Search?keyword=secret");
            assertThat(ScrapeDiagnostics.response(403, "https://search.jd.com/Search", "Forbidden")).isFalse();
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.ACCESS_DENIED);
            assertThat(trace.primaryFailure().httpStatus()).isEqualTo(403);
            assertThat(trace.primaryFailure().detail()).contains("无法仅凭状态码确认");
        }
    }

    @Test
    void distinguishesRateLimitCaptchaAndLoginIncludingHttp200() {
        Object[][] cases = {
            {429, "https://example.com/", "limited", ScrapeDiagnostics.Code.RATE_LIMITED},
            {200, "https://example.com/", "<html><title>安全验证</title></html>", ScrapeDiagnostics.Code.BLOCKED},
            {200, "https://example.com/", "<html><form><input name='captcha'></form></html>", ScrapeDiagnostics.Code.BLOCKED},
            {200, "https://passport.jd.com/new/login.aspx", "login", ScrapeDiagnostics.Code.AUTH_REQUIRED},
            {200, "https://example.com/login", "login", ScrapeDiagnostics.Code.AUTH_REQUIRED},
            {503, "https://example.com/", "unavailable", ScrapeDiagnostics.Code.HTTP_ERROR},
        };
        for (Object[] entry : cases) {
            try (var trace = ScrapeDiagnostics.open(message -> {})) {
                assertThat(ScrapeDiagnostics.response((int) entry[0], (String) entry[1], (String) entry[2])).isFalse();
                assertThat(trace.primaryFailure().code()).isEqualTo(entry[3]);
            }
        }
    }

    @Test
    void ordinaryBookMentioningCaptchaOrLoginIsNotAChallenge() {
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            assertThat(ScrapeDiagnostics.response(200, "https://example.com/search?q=captcha+login",
                    "<html><title>搜索结果</title><article>验证码算法与登录设计</article></html>")).isTrue();
            assertThat(ScrapeDiagnostics.response(200, "https://example.com/", "{\"title\":\"captcha\"}")).isTrue();
            assertThat(ScrapeDiagnostics.response(200, "https://book.douban.com/subject/123/",
                    "<html><title>验证码算法</title><h1><span>验证码算法</span></h1><div id='info'>出版社</div></html>")).isTrue();
            assertThat(trace.failures()).isEmpty();
        }
    }

    @Test
    void missingSelectorsAreNotSufficientEvidenceOfNoSearchResults() {
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.emptySearchPage(Jsoup.parse("<html><title>搜索</title><main>新页面结构</main></html>"));
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.UNRECOGNIZED_RESPONSE);
        }
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.emptySearchPage(Jsoup.parse("<div class='no-result'>没有找到相关书籍</div>"));
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.NO_MATCH);
        }
    }

    @Test
    void classifiesApiErrorsWithoutExposingTheirRawMessageOrApiKey() {
        List<String> logs = new ArrayList<>();
        try (var trace = ScrapeDiagnostics.open(logs::add)) {
            ScrapeDiagnostics.request("https://user:secret@www.googleapis.com/books/v1/volumes?key=secret#secret");
            ScrapeDiagnostics.exception(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "secret", null,
                    "{\"error\":{\"message\":\"secret\",\"errors\":[{\"reason\":\"dailyLimitExceeded\"}]}}".getBytes(), null));
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.QUOTA_EXCEEDED);
            assertThat(trace.primaryFailure().httpStatus()).isEqualTo(403);
            assertThat(trace.primaryFailure().endpoint()).isEqualTo("https://www.googleapis.com/books/v1/volumes");
            assertThat(logs.toString() + trace.failures()).doesNotContain("secret");
        }
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.response(400, "https://www.googleapis.com/", """
                    {"error":{"details":[{"reason":"API_KEY_INVALID"}]}}
                    """);
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.API_KEY_INVALID);
        }
    }

    @Test
    void distinguishesDnsAndTimeoutFromWrappedNetworkExceptions() {
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.exception(new ResourceAccessException("secret-url", new SocketTimeoutException("secret")));
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.TIMEOUT);
            assertThat(trace.failures().toString()).doesNotContain("secret");
        }
        try (var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.exception(new ResourceAccessException("secret-url", new UnknownHostException("secret")));
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.DNS_ERROR);
        }
    }

    @Test
    void partialFailureIsNotOverwrittenByLaterEmptySearchAndScopeIsCleanedUp() {
        List<String> logs = new ArrayList<>();
        try (var trace = ScrapeDiagnostics.open(logs::add)) {
            trace.stage("ISBN 查询");
            ScrapeDiagnostics.response(403, "https://example.com/", "forbidden");
            trace.stage("书名查询");
            ScrapeDiagnostics.report(ScrapeDiagnostics.Code.NO_MATCH, "未匹配");
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.ACCESS_DENIED);
            assertThat(trace.failures()).hasSize(2);
            assertThat(trace.failures().get(0).stage()).isEqualTo("ISBN 查询");
            assertThat(trace.failures().get(1).httpStatus()).isNull();
        }
        int logCount = logs.size();
        ScrapeDiagnostics.report(ScrapeDiagnostics.Code.NO_MATCH, "不应泄露到前一个任务");
        assertThat(logs).hasSize(logCount);
    }

    @Test
    void scopesAreIsolatedAcrossWorkers() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2);
                var trace = ScrapeDiagnostics.open(message -> {})) {
            ScrapeDiagnostics.report(ScrapeDiagnostics.Code.NO_MATCH, "当前任务");
            var code = executor.submit(() -> {
                try (var other = ScrapeDiagnostics.open(message -> {})) {
                    ScrapeDiagnostics.report(ScrapeDiagnostics.Code.BLOCKED, "另一任务");
                    return other.primaryFailure().code();
                }
            }).get();
            assertThat(code).isEqualTo(ScrapeDiagnostics.Code.BLOCKED);
            assertThat(trace.primaryFailure().code()).isEqualTo(ScrapeDiagnostics.Code.NO_MATCH);
            assertThat(trace.failures()).hasSize(1);
        }
    }
}
