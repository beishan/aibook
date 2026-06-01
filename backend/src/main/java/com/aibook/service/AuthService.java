package com.aibook.service;

import com.aibook.dto.AuthRequest;
import com.aibook.dto.AuthResponse;
import com.aibook.dto.RegisterRequest;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import com.aibook.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务（独立于 UserDetailsService，避免循环依赖）
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;

    /**
     * 用户注册
     */
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .role(User.Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    /**
     * 用户登录
     */
    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtUtils.generateToken(authentication);

        User user = (User) authentication.getPrincipal();

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
