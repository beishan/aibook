package com.aibook.controller;

import com.aibook.dto.ReadingProgressDTO;
import com.aibook.model.entity.User;
import com.aibook.service.ReadingProgressService;
import com.aibook.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReadingProgressControllerTest {

    @Test
    void acceptsStringSessionIdInReadingTimeHeartbeat() throws Exception {
        ReadingProgressService progressService = mock(ReadingProgressService.class);
        UserService userService = mock(UserService.class);
        User user = User.builder().id(1L).username("reader").build();
        when(userService.findByUsername("reader")).thenReturn(user);
        when(progressService.updateReadingTime(9L, 11L, user, 30L, "session-a"))
                .thenReturn(ReadingProgressDTO.builder().bookId(9L).versionId(11L).build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ReadingProgressController(progressService, userService)).build();

        mvc.perform(put("/api/reading-progress/book/9/time")
                        .param("versionId", "11")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "reader", "", java.util.List.of()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seconds\":30,\"sessionId\":\"session-a\"}"))
                .andExpect(status().isOk());

        verify(progressService).updateReadingTime(9L, 11L, user, 30L, "session-a");
    }
}
