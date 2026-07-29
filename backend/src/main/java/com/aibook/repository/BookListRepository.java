package com.aibook.repository;

import com.aibook.model.entity.BookList;
import com.aibook.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 从回收站永久移除前清理书单关联，不涉及任何书籍文件。
     */
    @Modifying
    @Query(value = "DELETE FROM book_list_items WHERE book_id = :bookId", nativeQuery = true)
    void deleteBookAssociations(@Param("bookId") Long bookId);
}
