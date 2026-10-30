package com.aibook.controller;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerMihomoService;
import com.aibook.service.crawler.CrawlerTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CrawlerPublicExecutorsControllerTest {
    @Mock private UserService users;
    @Mock private CrawlerTaskService tasks;
    @Mock private CrawlerMihomoService mihomo;
    @InjectMocks private CrawlerController controller;
    @InjectMocks private CrawlerMihomoController mihomoController;
    private MockMvc mvc;
    private final User user = User.builder().id(1L).build();

    @BeforeEach
    void setup() {
        when(users.findByUsername("reader")).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(controller, mihomoController).build();
    }

    @Test
    void listsPublicPoolWithoutQueuePathAndKeepsQueueListingAvailable() throws Exception {
        mvc.perform(get("/api/crawler/tasks/executors").principal(new UsernamePasswordAuthenticationToken("reader", "")))
                .andExpect(status().isOk());
        verify(tasks).queueExecutors(user, 0L);
        mvc.perform(get("/api/crawler/tasks/queues/9/executors").principal(new UsernamePasswordAuthenticationToken("reader", "")))
                .andExpect(status().isOk());
        verify(tasks).queueExecutors(user, 9L);
    }

    @Test
    void deletesPublicConfigurationThroughAccountContext() throws Exception {
        mvc.perform(delete("/api/crawler/tasks/executors/3").principal(new UsernamePasswordAuthenticationToken("reader", "")))
                .andExpect(status().isNoContent());
        verify(tasks).deleteQueueExecutor(user, 0L, 3L);
    }

    @Test
    void readsMihomoConfigurationWithoutAnyCreationQueue() throws Exception {
        mvc.perform(get("/api/crawler/tasks/executors/3/mihomo").principal(new UsernamePasswordAuthenticationToken("reader", "")))
                .andExpect(status().isOk());
        verify(mihomo).get(user, null, 3L);
    }
}
