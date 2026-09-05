package com.aibook.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aibook.model.entity.User;
import com.aibook.service.BookSeriesService;
import com.aibook.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BookSeriesControllerTest {
    @Test
    void callerCannotChooseAnotherUserThroughQueryParameters() throws Exception {
        User owner = User.builder().id(5L).username("reader").build();
        UserService users = mock(UserService.class);
        BookSeriesService series = mock(BookSeriesService.class);
        when(users.findByUsername("reader")).thenReturn(owner);
        when(series.books(owner, "三体")).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new BookSeriesController(series, users)).build();
        mvc.perform(get("/api/series/books").param("name", "三体").param("userId", "99")
                        .principal(new UsernamePasswordAuthenticationToken("reader", "unused")))
                .andExpect(status().isOk());
        verify(series).books(owner, "三体");
    }
}
