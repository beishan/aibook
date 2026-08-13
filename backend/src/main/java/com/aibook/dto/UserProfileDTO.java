package com.aibook.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDTO {
    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatarUrl;
    private Boolean hasAvatar;
    private String avatarVersion;
    private String mood;
    private String notes;
    private LocalDate birthDate;
    private String bookPreferences;
    private String role;
}
