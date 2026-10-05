package com.aibook.service.crawler;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MihomoApiClientTest {
    @Test
    void encodesUnicodeNamesUsesBearerAndConfirmsSelectedNode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> rawPath = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String response;
            if ("PUT".equals(exchange.getRequestMethod())) {
                rawPath.set(exchange.getRequestURI().getRawPath());
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            } else if (exchange.getRequestURI().getPath().endsWith("/delay")) {
                response = "{\"delay\":25}";
            } else {
                response = "{\"proxies\":{\"爬虫 专用\":{\"now\":\"香港 节点\"}}}";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            MihomoApiClient client = new MihomoApiClient(new ObjectMapper());
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(25, client.delay(url, "test-secret", "香港 节点"));
            client.select(url, "test-secret", "爬虫 专用", "香港 节点");
            assertEquals("Bearer test-secret", auth.get());
            assertTrue(rawPath.get().contains("%20"));
            assertFalse(rawPath.get().contains("+"));
            assertEquals("香港 节点", new ObjectMapper().readTree(body.get()).path("name").asText());
        } finally {
            server.stop(0);
        }
    }
}
