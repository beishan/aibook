package com.aibook.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

class JwtAuthenticationFilterDisabledUserTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotAuthenticateDisabledUserWithExistingToken() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        User disabledUser = User.builder()
                .username("disabled")
                .password("encoded")
                .enabled(false)
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtils.validateToken("token")).thenReturn(true);
        when(jwtUtils.getUsernameFromToken("token")).thenReturn("disabled");
        when(userDetailsService.loadUserByUsername("disabled")).thenReturn(disabledUser);
        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(jwtUtils, userDetailsService);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
