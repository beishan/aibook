package com.aibook.service.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MihomoApiClient {
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public JsonNode proxies(String url, String secret) throws Exception {
        return request(url, secret, "/proxies", null);
    }

    public int delay(String url, String secret, String node) throws Exception {
        JsonNode result = request(url, secret, "/proxies/" + encode(node)
                + "/delay?timeout=5000&url=" + encode("https://www.gstatic.com/generate_204"), null);
        if (!result.has("delay") || result.path("delay").asInt(-1) < 0) {
            throw new IOException("Mihomo 节点检测未返回有效延迟");
        }
        return result.get("delay").asInt();
    }

    public void select(String url, String secret, String group, String node) throws Exception {
        request(url, secret, "/proxies/" + encode(group), mapper.writeValueAsString(Map.of("name", node)));
        String actual = proxies(url, secret).path("proxies").path(group).path("now").asText();
        if (!node.equals(actual)) throw new IOException("Mihomo 切换后节点核对失败");
    }

    private JsonNode request(String url, String secret, String path, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url + path))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json");
        if (secret != null && !secret.isBlank()) request.header("Authorization", "Bearer " + secret);
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // Do not return remote response bodies or credentials to users/logs.
            throw new ApiException(response.statusCode());
        }
        return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
    }

    static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static class ApiException extends IOException {
        private final int status;

        public ApiException(int status) {
            super("Mihomo API 返回 HTTP " + status);
            this.status = status;
        }

        public int status() { return status; }
    }
}
