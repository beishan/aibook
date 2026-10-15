package com.aibook.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aibook.dto.crawler.CrawlerBatchImportDtos.JobView;
import com.aibook.model.entity.User;
import com.aibook.service.UserService;
import com.aibook.service.crawler.CrawlerBatchImportService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CrawlerBatchImportControllerTest {
    @Test
    void acceptsEntireBatchInOneRequestAndValidatesEmptySelections() throws Exception {
        User user = User.builder().id(1L).build();
        UserService users = mock(UserService.class);
        CrawlerBatchImportService service = mock(CrawlerBatchImportService.class);
        when(users.findByUsername("reader")).thenReturn(user);
        when(service.submit(user, List.of(10L, 11L), List.of("STRUCTURED")))
                .thenReturn(new JobView("batch", "RUNNING", 2, 0, List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new CrawlerBatchImportController(users, service)).build();
        var auth = new UsernamePasswordAuthenticationToken("reader", "");
        mvc.perform(post("/api/crawler/books/batch/import").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookIds\":[10,11],\"formats\":[\"STRUCTURED\"]}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.total").value(2));
        verify(service).submit(user, List.of(10L, 11L), List.of("STRUCTURED"));
        mvc.perform(post("/api/crawler/books/batch/import").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookIds\":[],\"formats\":[\"STRUCTURED\"]}"))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(service);
    }
}
