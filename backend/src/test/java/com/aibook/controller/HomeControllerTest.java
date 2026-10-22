package com.aibook.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aibook.model.entity.User;
import com.aibook.service.HomeService;
import com.aibook.service.UserService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerTest {
    @Test
    void dashboardUsesAuthenticatedAccountAndSerializesIndependentSections() throws Exception {
        var users = mock(UserService.class);
        var service = mock(HomeService.class);
        var owner = User.builder().id(42L).username("reader").build();
        when(users.findByUsername("reader")).thenReturn(owner);
        when(service.dashboard(owner)).thenReturn(new HomeService.Dashboard(Map.of("totalBooks", 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(new HomeController(service, users)).build();
        mvc.perform(get("/api/home?userId=99")
                .principal(new UsernamePasswordAuthenticationToken("reader", "unused")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalBooks").value(0))
                .andExpect(jsonPath("$.continuing").isArray())
                .andExpect(jsonPath("$.recentlyAdded").isEmpty());
        verify(service).dashboard(owner);
    }
}
