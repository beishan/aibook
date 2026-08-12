package com.aibook.repository;

import com.aibook.model.entity.RandomBookCover;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RandomBookCoverRepository extends JpaRepository<RandomBookCover, Long> {

    List<RandomBookCover> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<RandomBookCover> findByIdAndUser(Long id, User user);
}
