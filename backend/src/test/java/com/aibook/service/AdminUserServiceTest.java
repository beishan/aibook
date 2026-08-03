package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aibook.dto.AdminUserCreateRequest;
import com.aibook.dto.AdminUserDTO;
import com.aibook.dto.AdminUserUpdateRequest;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserServiceTest {

    @Test
    void createsUserWithEncodedPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        OperationLogService operationLogService = mock(OperationLogService.class);
        AdminUserService service = new AdminUserService(
                userRepository,
                passwordEncoder,
                operationLogService,
                mock(JdbcTemplate.class));
        User administrator = User.builder().id(1L).username("admin").build();
        AdminUserCreateRequest request = new AdminUserCreateRequest();
        request.setUsername("reader");
        request.setEmail("READER@example.com");
        request.setNickname("读者");
        request.setPassword("secret12");
        request.setRole("USER");
        request.setEnabled(true);

        when(passwordEncoder.encode("secret12")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });

        AdminUserDTO result = service.createUser(request, administrator);

        assertThat(result.getUsername()).isEqualTo("reader");
        assertThat(result.getEmail()).isEqualTo("reader@example.com");
        assertThat(result.getRole()).isEqualTo("USER");
        verify(passwordEncoder).encode("secret12");
    }

    @Test
    void preventsCurrentAdministratorFromDemotingOrDisablingSelf() {
        UserRepository userRepository = mock(UserRepository.class);
        User administrator = User.builder()
                .id(1L)
                .username("admin")
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(administrator));
        AdminUserService service = service(userRepository);
        AdminUserUpdateRequest request = updateRequest(
                "admin", "admin@example.com", "USER", false);

        assertThatThrownBy(() -> service.updateUser(1L, request, administrator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前登录账号");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void preventsDeletingCurrentAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        User administrator = User.builder()
                .id(1L)
                .username("admin")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(administrator));
        AdminUserService service = service(userRepository);

        assertThatThrownBy(() -> service.deleteUser(1L, administrator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能删除当前登录账号");
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void resetsPasswordUsingEncoder() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        User administrator = User.builder().id(1L).username("admin").build();
        User target = User.builder().id(2L).username("reader").password("old").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode("new-secret")).thenReturn("new-encoded");
        AdminUserService service = new AdminUserService(
                userRepository,
                passwordEncoder,
                mock(OperationLogService.class),
                mock(JdbcTemplate.class));

        service.resetPassword(2L, "new-secret", administrator);

        assertThat(target.getPassword()).isEqualTo("new-encoded");
        verify(userRepository).save(target);
    }

    @Test
    void deletesOwnedDatabaseDataButDoesNotPerformFilesystemOperations() {
        UserRepository userRepository = mock(UserRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        User administrator = User.builder()
                .id(1L)
                .username("admin")
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
        User target = User.builder()
                .id(2L)
                .username("reader")
                .role(User.Role.USER)
                .enabled(true)
                .build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        AdminUserService service = new AdminUserService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(OperationLogService.class),
                jdbcTemplate);

        service.deleteUser(2L, administrator);

        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
        verify(userRepository).delete(target);
        verify(userRepository).flush();
    }

    private AdminUserService service(UserRepository userRepository) {
        return new AdminUserService(
                userRepository,
                mock(PasswordEncoder.class),
                mock(OperationLogService.class),
                mock(JdbcTemplate.class));
    }

    private AdminUserUpdateRequest updateRequest(
            String username,
            String email,
            String role,
            boolean enabled) {
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setRole(role);
        request.setEnabled(enabled);
        return request;
    }
}
