package com.aibook.repository;

import com.aibook.model.entity.ShelfGroup;
import com.aibook.model.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShelfGroupRepository extends JpaRepository<ShelfGroup, Long> {

    List<ShelfGroup> findByUserOrderBySortOrderAscCreatedAtAsc(User user);

    Optional<ShelfGroup> findByIdAndUser(Long id, User user);

    boolean existsByUserAndNameIgnoreCase(User user, String name);

    boolean existsByUserAndNameIgnoreCaseAndIdNot(User user, String name, Long id);
}
