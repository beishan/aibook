package com.aibook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    @Builder.Default
    private String type = "Bearer";
    private String username;
    private String email;
    private String role;

    public AuthResponse(String token, String username, String email, String role) {
        this.token = token;
        this.type = "Bearer";
        this.username = username;
        this.email = email;
        this.role = role;
    }
}
