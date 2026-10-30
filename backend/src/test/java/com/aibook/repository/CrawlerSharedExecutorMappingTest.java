package com.aibook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.aibook.model.entity.CrawlerQueueExecutor;
import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

class CrawlerSharedExecutorMappingTest {
    @Test
    void sharedQueueCollectionAndRepositoryQueryCompileWithPostgresDialect() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var definition : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory()) {
                String hql = CrawlerQueueExecutorRepository.class
                        .getMethod("findByQueueIdOrderBySortOrderAscIdAsc", Long.class)
                        .getAnnotation(Query.class).value();
                var parsed = ((SessionFactoryImplementor) factory).getQueryEngine()
                        .getHqlTranslator().translate(hql, CrawlerQueueExecutor.class);
                assertThat(parsed).isNotNull();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
