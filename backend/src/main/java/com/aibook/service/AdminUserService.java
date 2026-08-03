package com.aibook.service;

import com.aibook.dto.AdminUserCreateRequest;
import com.aibook.dto.AdminUserDTO;
import com.aibook.dto.AdminUserUpdateRequest;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;

    @Transactional(readOnly = true)
    public Page<AdminUserDTO> getUsers(String keyword, Pageable pageable) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return userRepository.search(normalizedKeyword, pageable).map(this::toDTO);
    }

    @Transactional
    public AdminUserDTO createUser(AdminUserCreateRequest request, User administrator) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        requireUnique(username, email, null);
        User.Role role = parseRole(request.getRole());

        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .nickname(normalizeNickname(request.getNickname(), username))
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .build());
        operationLogService.record(
                administrator,
                OperationLog.Action.CREATE_USER,
                null,
                "新增用户“" + user.getUsername() + "”",
                "角色：" + user.getRole().name());
        return toDTO(user);
    }

    @Transactional
    public AdminUserDTO updateUser(
            Long id,
            AdminUserUpdateRequest request,
            User administrator) {
        User target = requireUser(id);
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User.Role newRole = parseRole(request.getRole());
        boolean newEnabled = request.getEnabled() == null
                ? Boolean.TRUE.equals(target.getEnabled())
                : request.getEnabled();

        if (target.getId().equals(administrator.getId())) {
            if (!target.getUsername().equals(username)) {
                throw new IllegalArgumentException("不能修改当前登录账号的用户名");
            }
            if (newRole != User.Role.ADMIN || !newEnabled) {
                throw new IllegalArgumentException("不能取消当前登录账号的管理员权限或禁用该账号");
            }
        }
        ensureAdminRemains(target, newRole, newEnabled);
        requireUnique(username, email, id);

        target.setUsername(username);
        target.setEmail(email);
        target.setNickname(normalizeNickname(request.getNickname(), username));
        target.setRole(newRole);
        target.setEnabled(newEnabled);
        User saved = userRepository.save(target);
        operationLogService.record(
                administrator,
                OperationLog.Action.UPDATE_USER,
                null,
                "修改用户“" + saved.getUsername() + "”",
                "角色：" + saved.getRole().name()
                        + "；状态：" + (Boolean.TRUE.equals(saved.getEnabled()) ? "启用" : "禁用"));
        return toDTO(saved);
    }

    @Transactional
    public void resetPassword(Long id, String password, User administrator) {
        User target = requireUser(id);
        target.setPassword(passwordEncoder.encode(password));
        userRepository.save(target);
        operationLogService.record(
                administrator,
                OperationLog.Action.RESET_PASSWORD,
                null,
                "重置用户“" + target.getUsername() + "”的密码",
                null);
    }

    /**
     * 删除用户及其数据库业务数据。书籍原文件始终保留。
     */
    @Transactional
    public void deleteUser(Long id, User administrator) {
        User target = requireUser(id);
        if (target.getId().equals(administrator.getId())) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }
        ensureAdminRemains(target, null, false);
        String targetUsername = target.getUsername();

        operationLogService.record(
                administrator,
                OperationLog.Action.DELETE_USER,
                null,
                "删除用户“" + targetUsername + "”",
                "用户数据库数据已清理，书籍原文件保留");
        deleteOwnedData(id);
        userRepository.delete(target);
        userRepository.flush();
        userProfileService.deleteStoredAvatar(target);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private User.Role parseRole(String role) {
        try {
            return User.Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalArgumentException("角色必须是 USER 或 ADMIN");
        }
    }

    private void requireUnique(String username, String email, Long excludedId) {
        boolean usernameExists = excludedId == null
                ? userRepository.existsByUsername(username)
                : userRepository.existsByUsernameAndIdNot(username, excludedId);
        if (usernameExists) {
            throw new IllegalArgumentException("用户名已存在");
        }
        boolean emailExists = excludedId == null
                ? userRepository.existsByEmail(email)
                : userRepository.existsByEmailAndIdNot(email, excludedId);
        if (emailExists) {
            throw new IllegalArgumentException("邮箱已被使用");
        }
    }

    private void ensureAdminRemains(
            User target,
            User.Role newRole,
            boolean newEnabled) {
        boolean removesActiveAdmin = target.getRole() == User.Role.ADMIN
                && Boolean.TRUE.equals(target.getEnabled())
                && (newRole != User.Role.ADMIN || !newEnabled);
        if (removesActiveAdmin
                && userRepository.countByRoleAndEnabledTrue(User.Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("系统必须至少保留一个已启用的管理员");
        }
    }

    private String normalizeNickname(String nickname, String username) {
        return nickname == null || nickname.trim().isEmpty()
                ? username
                : nickname.trim();
    }

    private void deleteOwnedData(Long userId) {
        String ownedBooks = "SELECT id FROM books WHERE user_id = ?";
        String ownedVersions = "SELECT bv.id FROM book_versions bv "
                + "JOIN books b ON b.id = bv.book_id WHERE b.user_id = ?";
        String ownedTasks = "SELECT id FROM text_repair_tasks "
                + "WHERE user_id = ? OR book_id IN (" + ownedBooks + ")";

        update("DELETE FROM text_repair_issues WHERE task_id IN (" + ownedTasks + ")",
                userId, userId);
        update("DELETE FROM text_repair_tasks WHERE user_id = ? OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM text_repair_rules WHERE user_id = ? OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM text_repair_templates WHERE user_id = ?", userId);

        update("DELETE FROM version_reading_progress WHERE user_id = ? OR version_id IN ("
                + ownedVersions + ")", userId, userId);
        update("DELETE FROM reading_progress WHERE user_id = ? OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM bookmarks WHERE user_id = ? OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM book_highlights WHERE user_id = ? OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM book_list_items WHERE book_list_id IN "
                + "(SELECT id FROM book_lists WHERE user_id = ?) OR book_id IN ("
                + ownedBooks + ")", userId, userId);
        update("DELETE FROM book_tags WHERE book_id IN (" + ownedBooks + ")", userId);
        update("DELETE FROM book_versions WHERE book_id IN (" + ownedBooks + ")", userId);
        update("DELETE FROM book_lists WHERE user_id = ?", userId);
        update("DELETE FROM scan_records WHERE user_id = ?", userId);
        update("DELETE FROM scan_directories WHERE user_id = ?", userId);
        update("DELETE FROM books WHERE user_id = ?", userId);
        update("UPDATE categories SET parent_id = NULL WHERE user_id = ?", userId);
        update("DELETE FROM categories WHERE user_id = ?", userId);
        update("DELETE FROM tags WHERE user_id = ?", userId);
        update("DELETE FROM operation_logs WHERE user_id = ?", userId);
    }

    private void update(String sql, Object... arguments) {
        jdbcTemplate.update(sql, arguments);
    }

    private AdminUserDTO toDTO(User user) {
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
