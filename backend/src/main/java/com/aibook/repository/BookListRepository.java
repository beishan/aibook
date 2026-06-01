package com.aibook.repository;

import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 书单 Repository
 */
@Repository
public interface BookListRepository extends JpaRepository<BookList, Long> {

    /**
     * 根据用户查询书单
     */
    List<BookList> findByUser(User user);

    /**
     * 根据用户和名称查询书单
     */
    BookList findByNameAndUser(String name, User user);
}
