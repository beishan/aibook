package com.aibook.config;

import com.aibook.model.entity.User;
import com.aibook.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 兼容升级前没有角色管理的私有部署，将最早创建的用户设为管理员。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (userRepository.countByRole(User.Role.ADMIN) > 0) {
            return;
        }
        userRepository.findFirstByOrderByCreatedAtAscIdAsc().ifPresent(user -> {
            user.setRole(User.Role.ADMIN);
            user.setEnabled(true);
            userRepository.save(user);
            log.warn("系统尚无管理员，已将唯一用户 {} 提升为管理员", user.getUsername());
        });
    }
}
