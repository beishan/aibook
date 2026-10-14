package com.aibook.service;

import com.aibook.dto.SimilarBookDTO;
import com.aibook.repository.BookRepository;
import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

/** 用真实实体映射校验投影查询，避免新增扫描接口阻止应用启动。 */
class BookSimilarityScanQueryTest {
    @Test
    void scanProjectionQueryCompilesAgainstActualEntities() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var definition : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory();
                    var session = factory.openSession()) {
                var query = BookRepository.class
                        .getMethod("findSimilarityScanBooks", com.aibook.model.entity.User.class)
                        .getAnnotation(Query.class).value();
                session.createQuery(query, SimilarBookDTO.class);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
