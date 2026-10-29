package com.aibook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aibook.model.entity.User;
import com.aibook.service.BookCardBadgeSettingsService;
import com.aibook.service.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class BookCardBadgeSettingsControllerTest {
    @Test
    void readsAndWritesOnlyTheAuthenticatedAccountsSettings() {
        var users = mock(UserService.class);
        var service = mock(BookCardBadgeSettingsService.class);
        var controller = new BookCardBadgeSettingsController(service, users);
        var user = User.builder().id(7L).username("reader").build();
        var authentication = new UsernamePasswordAuthenticationToken("reader", null);
        var settings = new BookCardBadgeSettingsService.Settings(Map.of("structured", "线"), 30);
        when(users.findByUsername("reader")).thenReturn(user);
        when(service.get(user)).thenReturn(settings);
        when(service.save(user, settings)).thenReturn(settings);
        assertThat(controller.get(authentication)).isSameAs(settings);
        assertThat(controller.save(authentication, settings)).isSameAs(settings);
        verify(service).get(user);
        verify(service).save(user, settings);
    }
}
