package com.aibook.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AdminBootstrapInitializerTest {

    @Test
    void promotesOldestUserWhenUpgradedSystemHasNoAdministrator() {
        UserRepository userRepository = mock(UserRepository.class);
        User oldestUser = User.builder()
                .id(1L)
                .username("owner")
                .role(User.Role.USER)
                .enabled(false)
                .build();
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(0L);
        when(userRepository.findFirstByOrderByCreatedAtAscIdAsc())
                .thenReturn(Optional.of(oldestUser));

        new AdminBootstrapInitializer(userRepository).run(mock(ApplicationArguments.class));

        assertThat(oldestUser.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(oldestUser.getEnabled()).isTrue();
        verify(userRepository).save(oldestUser);
    }
}
