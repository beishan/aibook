package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.dto.AuthResponse;
import com.aibook.dto.RegisterRequest;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.aibook.security.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceAdminRegistrationTest {

    @Test
    void assignsAdministratorRoleToFirstRegisteredUser() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        AuthService service = new AuthService(
                userRepository,
                passwordEncoder,
                jwtUtils,
                mock(AuthenticationManager.class));
        RegisterRequest request = new RegisterRequest(
                "admin", "admin@example.com", "secret12", "管理员");

        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("secret12")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateToken("admin")).thenReturn("token");

        AuthResponse response = service.register(request);

        assertThat(response.getRole()).isEqualTo("ADMIN");
    }
}
