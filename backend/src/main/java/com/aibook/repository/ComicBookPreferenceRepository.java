package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.ComicBookPreference;
import com.aibook.model.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComicBookPreferenceRepository extends JpaRepository<ComicBookPreference, Long> {

    Optional<ComicBookPreference> findByBookAndUser(Book book, User user);
}
