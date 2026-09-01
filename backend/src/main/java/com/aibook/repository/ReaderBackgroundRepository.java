package com.aibook.repository;

import com.aibook.model.entity.ReaderBackground;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReaderBackgroundRepository extends JpaRepository<ReaderBackground, Long> {

    List<ReaderBackground> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<ReaderBackground> findByIdAndUser(Long id, User user);
}
