package com.aibook.library;

import static org.junit.jupiter.api.Assertions.*;

import com.aibook.model.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Real PostgreSQL coverage. Uses a unique schema inside an explicitly supplied test database. */
@EnabledIfEnvironmentVariable(named = "LIBRARY_TEST_DATABASE_URL", matches = ".+")
class LibraryDatabaseTest {
    private JdbcTemplate jdbc;
    private DriverManagerDataSource source;
    private LibraryService service;
    private LibraryLifecycle lifecycle;
    private String schema;
    private User user = User.builder().id(1L).build();

    @BeforeEach
    void setup() {
        source = new DriverManagerDataSource(System.getenv("LIBRARY_TEST_DATABASE_URL"),
                System.getenv("LIBRARY_TEST_DATABASE_USER"), System.getenv("LIBRARY_TEST_DATABASE_PASSWORD"));
        schema = "library_it_" + UUID.randomUUID().toString().replace("-", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        source.setUrl(System.getenv("LIBRARY_TEST_DATABASE_URL") + "?currentSchema=" + schema);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE books(id BIGINT PRIMARY KEY,user_id BIGINT,title TEXT,author TEXT,format TEXT,"
                + "cover_url TEXT,cover_hidden BOOLEAN,reading_status TEXT,category_id BIGINT,deleted_at TIMESTAMP,purged_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE reading_progress(user_id BIGINT,book_id BIGINT,total_progress INT,"
                + "current_chapter_title TEXT,last_read_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE book_tags(book_id BIGINT,tag_id BIGINT)");
        jdbc.update("INSERT INTO users VALUES(1),(2)");
        jdbc.update("INSERT INTO books(id,user_id,title,format,reading_status) VALUES "
                + "(1,1,'第一本','epub','READING'),(2,1,'第二本','epub','UNREADING'),"
                + "(3,2,'他人书籍','epub','UNREADING'),(4,1,'纯有声',NULL,'UNREADING')");
        var schemaInitializer = new LibrarySchema(jdbc);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> schemaInitializer.run(null));
        service = new LibraryService(new NamedParameterJdbcTemplate(source), new ObjectMapper());
        lifecycle = new LibraryLifecycle(jdbc);
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private long library(String name) { return ((Number) service.create(user,name,"",Map.of()).get("id")).longValue(); }

    @Test
    void isolatesAccountsDeduplicatesAndReusesRealProgress() {
        long a = library("A"), b = library("B");
        var result = service.add(user,List.of(a,b),List.of(1L,3L,4L),null);
        assertEquals(2,result.stream().filter(row -> row.status().equals("added")).count());
        assertEquals(4,result.stream().filter(row -> row.status().equals("failed")).count());
        assertEquals("existing",service.add(user,List.of(a),List.of(1L),null).get(0).status());
        var partial = service.add(user,List.of(a,999L),List.of(2L),null);
        assertEquals(1,partial.stream().filter(row -> row.status().equals("failed")).count());
        assertEquals(1,partial.stream().filter(row -> row.status().equals("added")).count());
        assertThrows(ResponseStatusException.class, () -> service.library(User.builder().id(2L).build(),a));
        assertThrows(ResponseStatusException.class, () -> service.save(user,a,10,"A","",Map.of()));
        jdbc.update("INSERT INTO reading_progress VALUES(1,1,42,'第二章',CURRENT_TIMESTAMP)");
        for (long id : List.of(a,b)) {
            var page = service.books(user,id,"",null,null,false,null,null,null,"recent",0,24);
            assertEquals(42,page.content().get(0).get("totalProgress"));
        }
        service.item(user,a,1,null,true,true,false);
        assertEquals(1,service.books(user,a,"",null,null,true,null,null,null,"favorite",0,24).totalElements());
        assertEquals(0,service.books(user,b,"",null,null,true,null,null,null,"favorite",0,24).totalElements());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM reading_progress",Integer.class));
    }

    @Test
    void removesOnlyScopedRelationsAndRestoresSoftDeletedBooks() {
        long a = library("A");
        service.collectionSave(user,a,"groups",null,"分组","",false);
        service.collectionSave(user,a,"lists",null,"书单","",true);
        long group = jdbc.queryForObject("SELECT id FROM personal_library_groups",Long.class);
        long list = jdbc.queryForObject("SELECT id FROM personal_library_lists",Long.class);
        service.add(user,List.of(a),List.of(1L,2L),null);
        assertEquals("added",service.add(user,List.of(a),List.of(1L),list).get(0).status());
        assertEquals("existing",service.add(user,List.of(a),List.of(1L),list).get(0).status());
        service.add(user,List.of(a),List.of(2L),list);
        service.item(user,a,1,group,true,true,true);
        service.collectionDelete(user,a,"groups",group);
        assertNull(jdbc.queryForObject("SELECT group_id FROM personal_library_items WHERE book_id=1",Long.class));
        jdbc.update("UPDATE books SET deleted_at=CURRENT_TIMESTAMP WHERE id=1");
        assertEquals(1,service.books(user,a,"",null,null,false,null,null,null,"added",0,24).totalElements());
        jdbc.update("UPDATE books SET deleted_at=NULL WHERE id=1");
        assertEquals(2,service.books(user,a,"",null,null,false,null,null,null,"added",0,24).totalElements());
        service.remove(user,a,List.of(1L),null);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_list_items",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM books",Integer.class));
        service.delete(user,a);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_items",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM books",Integer.class));
    }

    @Test
    void mergesCollectionsAndPreservesTargetGroupAndCleansPurgeTombstones() {
        long a = library("A");
        service.collectionSave(user,a,"lists",null,"书单","",true);
        long list = jdbc.queryForObject("SELECT id FROM personal_library_lists",Long.class);
        service.add(user,List.of(a),List.of(1L,2L),list);
        service.item(user,a,2,null,true,true,false);
        lifecycle.merge(1,2);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_items",Integer.class));
        assertEquals(true,jdbc.queryForObject("SELECT favorite FROM personal_library_items",Boolean.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_list_items",Integer.class));
        lifecycle.purge(1);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM personal_library_list_items",Integer.class));
    }

    @Test
    void settingsPagingAndOrderUseDatabaseConstraintsAndOwnerScope() {
        long a = library("A"), b = library("B");
        service.pageSize(user,48);
        assertEquals(48,service.pageSize(user));
        assertEquals(24,service.pageSize(User.builder().id(2L).build()));
        service.save(user,a,0,"A","描述",Map.of("modules",List.of("recent"),"accent","site"));
        assertEquals(1L,((Number) service.library(user,a).get("revision")).longValue());
        assertThrows(ResponseStatusException.class, () -> service.save(user,a,0,"A","",Map.of()));
        service.order(user,a,"libraries",a,b,null);
        assertEquals(b,((Number)service.libraries(user,"",0,24,"manual").content().get(0).get("id")).longValue());
        service.add(user,List.of(a),List.of(1L,2L),null);
        service.order(user,a,"books",1,2,null);
        assertEquals(2L,((Number)service.books(user,a,"",null,null,false,null,null,null,"manual",0,24).content().get(0).get("id")).longValue());
    }
    @Test
    void rejectsForeignGroupsAndRollsBackWholeBatchOnAnInvalidMember() {
        long a = library("A"), b = library("B");
        service.collectionSave(user,b,"groups",null,"B分组","",false);
        long group = jdbc.queryForObject("SELECT id FROM personal_library_groups",Long.class);
        service.add(user,List.of(a),List.of(1L),null);
        assertThrows(ResponseStatusException.class, () -> service.item(user,a,1,group,null,null,true));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThrows(ResponseStatusException.class, () -> transaction.executeWithoutResult(status ->
                service.items(user,a,List.of(1L,3L),null,true,false)));
        assertFalse(jdbc.queryForObject("SELECT favorite FROM personal_library_items",Boolean.class));
        assertEquals(List.of(1L),service.membership(user,a,List.of(1L,3L),null));
        assertThrows(ResponseStatusException.class, () -> service.add(user,List.of(a),List.of(1L),999L));
    }

    @Test
    void pagesLargeCollectionsWithBoundedPayloadAndRecordsLocalBaseline() {
        jdbc.update("INSERT INTO books(id,user_id,title,format,reading_status) "
                + "SELECT n,1,'书籍'||n,'txt','UNREADING' FROM generate_series(100,10099) n");
        jdbc.update("INSERT INTO personal_libraries(user_id,name,sort_order) "
                + "SELECT 1,'馆'||n,n FROM generate_series(1,100) n");
        long id = jdbc.queryForObject("SELECT MIN(id) FROM personal_libraries",Long.class);
        jdbc.update("INSERT INTO personal_library_items(library_id,book_id,sort_order) "
                + "SELECT ?,n,n FROM generate_series(100,5099) n",id);
        List<Long> times = new java.util.ArrayList<>();
        for (int i=0;i<16;i++) {
            long start = System.nanoTime();
            assertEquals(24,service.books(user,id,"",null,null,false,null,null,null,"added",0,24).content().size());
            assertEquals(24,service.libraries(user,"",0,24,"manual").content().size());
            service.home(user,id);
            if (i>0) times.add((System.nanoTime()-start)/1_000_000);
        }
        times.sort(Long::compare);
        long p95 = times.get(14);
        System.out.println("Personal library local PostgreSQL baseline: 100 libraries / 10,000 books / 5,000 members; "
                + "book page + library page + home combined warm P95=" + p95 + "ms");
        assertTrue(p95 < 2000, "Combined local warm queries must finish below the 2s development threshold");
    }

}
