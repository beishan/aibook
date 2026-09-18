package com.aibook.config;

import com.aibook.model.entity.Book;
import com.aibook.repository.BookRepository;
import com.aibook.service.FullTextSearchSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 数据库全文检索初始化器。
 *
 * <p>项目未引入 Flyway/Liquibase，且 {@code ddl-auto=update} 无法管理
 * tsvector 列、GIN 索引与触发器，因此在应用启动后以幂等方式完成：</p>
 * <ol>
 *   <li>启用 zhparser 扩展并创建 chinese_zh 中文分词配置；</li>
 *   <li>为 books 表增加 search_vector 列、GIN 索引与自动维护触发器；</li>
 *   <li>回填存量书籍的全文向量与拼音检索串；</li>
 *   <li>zhparser 不可用（如本地开发环境）时记录警告并保持 LIKE 降级。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FullTextSearchInitializer implements ApplicationRunner {

    private static final int PINYIN_BACKFILL_BATCH_SIZE = 200;

    /** 触发器函数：书名/作者/ISBN/出版社/简介变更时自动重算全文向量。 */
    private static final String TRIGGER_FUNCTION_SQL = """
            CREATE OR REPLACE FUNCTION aibook_books_search_vector_update() RETURNS trigger AS $fn$
            BEGIN
              NEW.search_vector := to_tsvector('chinese_zh',
                  coalesce(NEW.title, '') || ' ' || coalesce(NEW.author, '') || ' ' ||
                  coalesce(NEW.isbn, '') || ' ' || coalesce(NEW.publisher, '') || ' ' ||
                  coalesce(NEW.description, ''));
              RETURN NEW;
            END
            $fn$ LANGUAGE plpgsql
            """;

    /** 触发器：仅在这些列出现在 UPDATE SET 列表时重算，避免无谓开销。 */
    private static final String TRIGGER_SQL = """
            CREATE TRIGGER trg_books_search_vector
            BEFORE INSERT OR UPDATE OF title, author, isbn, publisher, description ON books
            FOR EACH ROW EXECUTE FUNCTION aibook_books_search_vector_update()
            """;

    /** 存量数据回填（SET 列不含分词源列，不会触发触发器）。 */
    private static final String BACKFILL_SQL = """
            UPDATE books SET search_vector = to_tsvector('chinese_zh',
                coalesce(title, '') || ' ' || coalesce(author, '') || ' ' ||
                coalesce(isbn, '') || ' ' || coalesce(publisher, '') || ' ' ||
                coalesce(description, ''))
            WHERE search_vector IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;
    private final BookRepository bookRepository;
    private final FullTextSearchSupport fullTextSearchSupport;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        initFullTextSearch();
        backfillSearchPinyin();
    }

    private void initFullTextSearch() {
        try {
            Integer available = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_available_extensions WHERE name = 'zhparser'",
                    Integer.class);
            if (available == null || available == 0) {
                log.warn("PostgreSQL 未安装 zhparser 扩展，书籍搜索降级为 LIKE + 拼音匹配；"
                        + "部署 zhparser 定制镜像后重启即可启用中文全文检索");
                return;
            }

            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS zhparser");
            // 重建配置保证分词映射始终与代码一致；已有 search_vector 数据不受影响。
            jdbcTemplate.execute("DROP TEXT SEARCH CONFIGURATION IF EXISTS chinese_zh");
            jdbcTemplate.execute("CREATE TEXT SEARCH CONFIGURATION chinese_zh (PARSER = zhparser)");
            jdbcTemplate.execute("ALTER TEXT SEARCH CONFIGURATION chinese_zh "
                    + "ADD MAPPING FOR n,v,a,i,e,l WITH simple");
            jdbcTemplate.execute("ALTER TABLE books ADD COLUMN IF NOT EXISTS search_vector tsvector");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_books_search_vector "
                    + "ON books USING GIN (search_vector)");
            jdbcTemplate.execute(TRIGGER_FUNCTION_SQL);
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_books_search_vector ON books");
            jdbcTemplate.execute(TRIGGER_SQL);

            int updated = jdbcTemplate.update(BACKFILL_SQL);
            if (updated > 0) {
                log.info("书籍全文检索向量回填完成，共 {} 本", updated);
            }
            fullTextSearchSupport.enableFullText();
            log.info("zhparser 中文全文检索已启用（配置 chinese_zh）");
        } catch (Exception e) {
            log.warn("全文检索初始化失败，书籍搜索降级为 LIKE + 拼音匹配：{}", e.getMessage());
        }
    }

    /** 拼音检索串不依赖数据库扩展，任何环境都可回填；保存时触发实体回调自动生成。 */
    private void backfillSearchPinyin() {
        try {
            int total = 0;
            Long afterId = 0L;
            while (true) {
                List<Book> batch = bookRepository.findPinyinBackfillCandidates(
                        afterId, PageRequest.of(0, PINYIN_BACKFILL_BATCH_SIZE));
                if (batch.isEmpty()) {
                    break;
                }
                transactionTemplate.executeWithoutResult(status ->
                        bookRepository.saveAll(batch));
                total += batch.size();
                afterId = batch.get(batch.size() - 1).getId();
            }
            if (total > 0) {
                log.info("书籍拼音检索串回填完成，共 {} 本", total);
            }
        } catch (Exception e) {
            log.warn("拼音检索串回填失败，新增/编辑书籍时会自动补齐：{}", e.getMessage());
        }
    }
}
