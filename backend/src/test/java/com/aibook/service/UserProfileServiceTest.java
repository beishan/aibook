package com.aibook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.dto.UserProfileDTO;
import com.aibook.dto.UserProfileUpdateRequest;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class UserProfileServiceTest {

    @TempDir
    Path tempDir;

    private UserRepository userRepository;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new UserProfileService(
                userRepository, mock(OperationLogService.class));
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesAndClearsProfileFields() {
        User user = user();
        user.setProfileNotes("旧备注");
        UserProfileUpdateRequest request = new UserProfileUpdateRequest();
        request.setNickname("  小书虫  ");
        request.setMood("  今天想读书  ");
        request.setNotes("   ");
        request.setBirthDate(LocalDate.of(1990, 5, 12));
        request.setBookPreferences("  科幻、历史  ");

        UserProfileDTO result = service.updateProfile(user, request);

        assertThat(result.getNickname()).isEqualTo("小书虫");
        assertThat(result.getMood()).isEqualTo("今天想读书");
        assertThat(result.getNotes()).isNull();
        assertThat(result.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(result.getBookPreferences()).isEqualTo("科幻、历史");
    }

    @Test
    void storesValidatedAvatarInsideUserDirectory() throws Exception {
        User user = user();
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", png);

        UserProfileDTO result = service.uploadAvatar(user, file);

        assertThat(result.getHasAvatar()).isTrue();
        assertThat(result.getAvatarUrl()).isEqualTo("/api/user/profile/avatar");
        assertThat(result.getAvatarVersion()).isNotBlank();
        Path stored = tempDir.resolve(user.getAvatarUrl());
        assertThat(stored).exists();
        assertThat(Files.readAllBytes(stored)).isEqualTo(png);
        assertThat(service.getAvatar(user).contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsFileWhoseContentIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.uploadAvatar(user(), file))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void removesStoredAvatarAndClearsProfileFlag() throws Exception {
        User user = user();
        byte[] png = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };
        service.uploadAvatar(user, new MockMultipartFile(
                "file", "avatar.png", "image/png", png));
        Path stored = tempDir.resolve(user.getAvatarUrl());

        UserProfileDTO result = service.deleteAvatar(user);

        assertThat(result.getHasAvatar()).isFalse();
        assertThat(result.getAvatarUrl()).isNull();
        assertThat(result.getAvatarVersion()).isNull();
        assertThat(stored).doesNotExist();
    }

    private User user() {
        return User.builder()
                .id(7L)
                .username("reader")
                .email("reader@example.com")
                .password("encoded")
                .role(User.Role.USER)
                .enabled(true)
                .build();
    }
}
