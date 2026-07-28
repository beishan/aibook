package com.aibook.service;

import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private static final Set<String> WEB_THEMES = Set.of("modern", "warm", "natural");
    private static final Set<String> LIBRARY_VIEW_MODES = Set.of("card", "list");

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /**
     * 根据用户名查找用户
     */
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    public UserPreferencesDTO getPreferences(String username) {
        User user = findByUsername(username);
        return toPreferences(user);
    }

    @Transactional
    public UserPreferencesDTO updatePreferences(
            String username,
            UserPreferencesDTO request) {
        User user = findByUsername(username);

        if (request.getTheme() != null) {
            requireAllowed("主题", request.getTheme(), WEB_THEMES);
            user.setWebTheme(request.getTheme());
        }
        if (request.getLibraryViewMode() != null) {
            requireAllowed(
                    "书库显示方式",
                    request.getLibraryViewMode(),
                    LIBRARY_VIEW_MODES);
            user.setLibraryViewMode(request.getLibraryViewMode());
        }

        return toPreferences(userRepository.save(user));
    }

    private UserPreferencesDTO toPreferences(User user) {
        return UserPreferencesDTO.builder()
                .theme(user.getWebTheme())
                .libraryViewMode(user.getLibraryViewMode())
                .build();
    }

    private void requireAllowed(String label, String value, Set<String> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(label + "不支持该值: " + value);
        }
    }
}
