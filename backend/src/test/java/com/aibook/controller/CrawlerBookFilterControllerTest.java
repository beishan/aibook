package com.aibook.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerManagementService;
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
class CrawlerBookFilterControllerTest {
    @Mock private UserService users;
    @Mock private CrawlerManagementService management;
    @InjectMocks private CrawlerController controller;
    private MockMvc mvc;
    private final User user = User.builder().id(7L).build();
    private final UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("reader", "unused");

    @BeforeEach
    void setup() {
        lenient().when(users.findByUsername("reader")).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void forwardsChapterCountWithOtherFiltersAndAccount() throws Exception {
        mvc.perform(get("/api/crawler/books").principal(auth)
                        .param("chapterCountGreaterThan", "100").param("page", "2")
                        .param("size", "50").param("keyword", "三体").param("siteId", "7")
                        .param("crawlStatus", "COMPLETED").param("importStatus", "READY")
                        .param("favoriteOnly", "true").param("tag", "科幻"))
                .andExpect(status().isOk());
        verify(management).books(user, 2, 50, "三体", 7L, "COMPLETED", "READY",
                true, "CREATED_DESC", "科幻", 100);
    }

    @Test
    void omittedThresholdKeepsExistingRequestsCompatible() throws Exception {
        mvc.perform(get("/api/crawler/books").principal(auth)).andExpect(status().isOk());
        verify(management).books(user, 0, 20, null, null, null, null,
                false, "CREATED_DESC", null, null);
    }

    @Test
    void rejectsFractionalAndOverflowThresholds() throws Exception {
        for (String threshold : new String[] {"1.5", "2147483648", "abc"}) {
            mvc.perform(get("/api/crawler/books").principal(auth)
                            .param("chapterCountGreaterThan", threshold))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(management);
    }
}
