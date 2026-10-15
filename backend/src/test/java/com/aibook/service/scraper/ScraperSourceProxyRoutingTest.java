package com.aibook.service.scraper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import org.jsoup.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScraperSourceProxyRoutingTest {
    @Test
    @SuppressWarnings("unchecked")
    void htmlSourcesSendActualRequestThroughTheChosenProxy() throws Exception {
        Proxy chosen = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 8899));
        for (Object scraper : new Object[] {spy(new DoubanScraper()), spy(new JdBookScraper())}) {
            var service = mock(ScraperProxyService.class);
            when(service.execute(anyString(), any())).thenAnswer(call -> {
                ScraperProxyService.Request<?> request = call.getArgument(1);
                return request.run(chosen);
            });
            ReflectionTestUtils.setField(scraper, "proxyService", service);
            Connection connection = mock(Connection.class, RETURNS_SELF);
            Connection.Response response = mock(Connection.Response.class);
            when(connection.execute()).thenReturn(response);
            when(response.statusCode()).thenReturn(200);
            when(response.url()).thenReturn(new URL("https://example.com/search"));
            when(response.body()).thenReturn("<html><title>正常搜索页</title></html>");
            if (scraper instanceof DoubanScraper douban) {
                doReturn(connection).when(douban).createConnection("https://example.com/search");
            } else {
                doReturn(connection).when((JdBookScraper) scraper).createConnection("https://example.com/search");
            }
            String body = ReflectionTestUtils.invokeMethod(scraper, "makeRequest", "https://example.com/search");
            assertThat(body).contains("正常搜索页");
            verify(connection).proxy(chosen);
            verify(service).execute(eq(((MetadataScraper) scraper).getConfigKey()), any());
        }
    }

    @Test
    void jsonSourcesUseSourceSpecificProxyGatewayWithoutMutatingSharedTemplate() throws Exception {
        var service = mock(ScraperProxyService.class);
        when(service.getJson(anyString(), anyString())).thenReturn("{\"items\":[]}");
        for (Object scraper : new Object[] {new GoogleBooksScraper(mock(com.aibook.service.SystemConfigService.class)),
                new OpenLibraryScraper()}) {
            ReflectionTestUtils.setField(scraper, "proxyService", service);
            String body = ReflectionTestUtils.invokeMethod(scraper, "requestJson", "https://example.com/books");
            assertThat(body).contains("items");
            verify(service).getJson(((MetadataScraper) scraper).getConfigKey(), "https://example.com/books");
        }
    }
}
