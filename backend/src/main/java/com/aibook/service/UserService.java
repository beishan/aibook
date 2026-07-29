package com.aibook.service;

import com.aibook.config.ScanSettings;
import com.aibook.dto.UserPreferencesDTO;
import com.aibook.model.entity.User;
import com.aibook.repository.FontAssetRepository;
import com.aibook.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
public class UserService implements UserDetailsService {

    private static final Set<String> WEB_THEMES = Set.of("modern", "warm", "natural");
    private static final Set<String> LIBRARY_VIEW_MODES = Set.of("card", "list");

    private final UserRepository userRepository;
    private final FontAssetRepository fontAssetRepository;

    /**
     * 保留单参数构造器，兼容轻量控制器测试中的 StubUserService。
     */
    public UserService(UserRepository userRepository) {
        this(userRepository, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            FontAssetRepository fontAssetRepository) {
        this.userRepository = userRepository;
        this.fontAssetRepository = fontAssetRepository;
    }

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
        if (request.getScanThreadCount() != null) {
            if (!ScanSettings.isValidThreadCount(request.getScanThreadCount())) {
                throw new IllegalArgumentException(
                        "扫描线程数必须在 "
                                + ScanSettings.MIN_THREAD_COUNT
                                + " 到 "
                                + ScanSettings.MAX_THREAD_COUNT
                                + " 之间");
            }
            user.setScanThreadCount(request.getScanThreadCount());
        }
        if (request.hasUiFontId()) {
            validateFont(request.getUiFontId());
            user.setUiFontId(request.getUiFontId());
        }
        if (request.hasReaderFontId()) {
            validateFont(request.getReaderFontId());
            user.setReaderFontId(request.getReaderFontId());
        }

        return toPreferences(userRepository.save(user));
    }

    private UserPreferencesDTO toPreferences(User user) {
        return UserPreferencesDTO.builder()
                .theme(user.getWebTheme())
                .libraryViewMode(user.getLibraryViewMode())
                .scanThreadCount(
                        ScanSettings.normalizeThreadCount(user.getScanThreadCount()))
                .uiFontId(activeFontId(user.getUiFontId()))
                .readerFontId(activeFontId(user.getReaderFontId()))
                .build();
    }

    private void validateFont(Long id) {
        if (id == null) {
            return;
        }
        if (fontAssetRepository == null
                || fontAssetRepository.findByIdAndEnabledTrue(id).isEmpty()) {
            throw new IllegalArgumentException("字体不存在或未启用: " + id);
        }
    }

    private Long activeFontId(Long id) {
        if (id == null || fontAssetRepository == null) {
            return id;
        }
        return fontAssetRepository.findByIdAndEnabledTrue(id).isPresent()
                ? id
                : null;
    }

    private void requireAllowed(String label, String value, Set<String> allowedValues) {
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException(label + "不支持该值: " + value);
        }
    }
}
