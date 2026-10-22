package com.aibook.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aibook.dto.recommendation.*;
import com.aibook.model.entity.User;
import com.aibook.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RecommendationControllerTest {
    @Test
    void endpointsResolveTheAuthenticatedAccountAndDoNotAcceptCallerOwnedUserIds() throws Exception {
        var service = mock(RecommendationService.class);
        var candidates = mock(RecommendationCandidates.class);
        var users = mock(UserService.class);
        var user = User.builder().id(42L).username("reader").build();
        when(users.findByUsername("reader")).thenReturn(user);
        when(service.preview(eq(user), any())).thenReturn(12);
        var mvc = MockMvcBuilders.standaloneSetup(new RecommendationController(service, candidates, users)).build();
        var principal = new UsernamePasswordAuthenticationToken("reader", "unused");
        var filter = new RecommendationFilter(List.of(), false, List.of(), false, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null, 6, 7, false);
        var request = new RecommendationRuleRequest("每日随缘", true, 0, filter);
        var json = new ObjectMapper().writeValueAsString(request);
        mvc.perform(get("/api/recommendations/today").principal(principal)).andExpect(status().isOk());
        mvc.perform(get("/api/recommendations/rules").principal(principal)).andExpect(status().isOk());
        mvc.perform(get("/api/recommendations/options").principal(principal)).andExpect(status().isOk());
        mvc.perform(post("/api/recommendations/preview").principal(principal)
                .contentType("application/json").content(json)).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(12));
        mvc.perform(post("/api/recommendations/rules").principal(principal)
                .contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(put("/api/recommendations/rules/3").principal(principal)
                .contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(post("/api/recommendations/rules/3/reroll").principal(principal)).andExpect(status().isOk());
        mvc.perform(delete("/api/recommendations/rules/3").principal(principal)).andExpect(status().isOk());
        verify(service).today(user);
        verify(service).rules(user);
        verify(candidates).options(user);
        verify(service).preview(user, request);
        verify(service).save(user, null, request);
        verify(service).save(user, 3L, request);
        verify(service).reroll(user, 3L);
        verify(service).delete(user, 3L);
    }
}
