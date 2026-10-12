package com.aibook.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aibook.dto.crawler.CrawlerDtos.*;
import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerStatisticsService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class CrawlerStatisticsControllerTest {
    @Mock private UserService users;
    @Mock private CrawlerStatisticsService statisticsService;
    @InjectMocks private CrawlerController controller;
    private MockMvc mvc;
    private final User user = User.builder().id(7L).build();
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("reader", "unused");

    @BeforeEach
    void setup() {
        when(users.findByUsername("reader")).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void keepsTheLegacyDaysParameterAndDailyResponseShape() throws Exception {
        var end = LocalDateTime.of(2026, 10, 1, 10, 30);
        when(statisticsService.dashboard(user, "day", 7)).thenReturn(new TimeBucketDashboardView(
                "day", 7, 7, end.minusDays(6).toLocalDate().atStartOfDay(), end,
                List.of(new TimeBucketStatisticsView("2026-10-01", 1, 1, 1, 1, 1, 1, 0)),
                List.of(), new CrawlerFunnelView(1, 1, 1, 1)));
        mvc.perform(get("/api/crawler/dashboard/statistics").param("days", "7").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.daily[0].date").value("2026-10-01"))
                .andExpect(jsonPath("$.daily[0].newChapters").value(1));
    }

    @Test
    void forwardsHourRangeAndAuthenticatedAccount() throws Exception {
        mvc.perform(get("/api/crawler/dashboard/statistics").param("granularity", "hour")
                .param("range", "48").principal(auth)).andExpect(status().isOk());
        verify(statisticsService).dashboard(user, "hour", 48);
    }

    @Test
    void bindsMinuteSnapshotAndPagination() throws Exception {
        var end = LocalDateTime.of(2026, 10, 1, 0, 1, 30);
        mvc.perform(get("/api/crawler/dashboard/chapter-attempts")
                .param("granularity", "minute").param("range", "60")
                .param("end", end.toString()).param("page", "2").param("size", "20")
                .principal(auth)).andExpect(status().isOk());
        verify(statisticsService).attempts(user, "minute", 60, end, 2, 20);
    }
}
