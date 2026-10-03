package com.aibook.repository;

import com.aibook.model.entity.Book;
import com.aibook.model.entity.CrawlerBook;
import com.aibook.model.entity.User;
import jakarta.persistence.Entity;
import java.util.Arrays;
import org.hibernate.Session;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 Hibernate 映射和查询编译器验证旧实体保存不会更新入库关联，无需连接数据库。 */
class CrawlerLibraryImportMappingTest {
    @Test
    void excludesImportFieldsFromEntityUpdatesAndAcceptsDedicatedMutationQueries() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            try (var factory = (SessionFactoryImplementor) sources.buildMetadata().buildSessionFactory();
                    Session session = factory.openSession()) {
                var persister = factory.getRuntimeMetamodels().getMappingMetamodel()
                        .getEntityDescriptor(CrawlerBook.class);
                var propertyNames = Arrays.asList(persister.getPropertyNames());
                assertThat(propertyNames).contains("libraryBook", "importStatus");
                assertThat(persister.getPropertyUpdateability()[propertyNames.indexOf("libraryBook")]).isFalse();
                assertThat(persister.getPropertyUpdateability()[propertyNames.indexOf("importStatus")]).isFalse();

                String linkQuery = CrawlerBookRepository.class.getMethod("linkLibraryBook",
                        Long.class, User.class, Book.class, CrawlerBook.ImportStatus.class)
                        .getAnnotation(Query.class).value();
                session.createMutationQuery(linkQuery);
                String statusQuery = CrawlerBookRepository.class.getMethod("updateImportStatus",
                        Long.class, CrawlerBook.ImportStatus.class, CrawlerBook.ImportStatus.class)
                        .getAnnotation(Query.class).value();
                session.createMutationQuery(statusQuery);
                String candidatesQuery = CrawlerBookRepository.class.getMethod("findCategoryCleanupCandidates",
                        User.class, Long.class, Long.class, org.springframework.data.domain.Pageable.class)
                        .getAnnotation(Query.class).value();
                session.createQuery(candidatesQuery, Object[].class);
                String clearCrawlerQuery = CrawlerBookRepository.class.getMethod("clearCategoryIfUnchanged",
                        Long.class, User.class, String.class).getAnnotation(Query.class).value();
                session.createMutationQuery(clearCrawlerQuery);
                String clearLibraryQuery = BookRepository.class.getMethod("clearCrawlerCategoryIfUnchanged",
                        Long.class, User.class, Long.class).getAnnotation(Query.class).value();
                session.createMutationQuery(clearLibraryQuery);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
