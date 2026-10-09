package com.aibook.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.aibook.model.entity.CrawlerMihomoPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class CrawlerMihomoRuntimeQueryTest {
    @Test
    void hibernateAcceptsRevisionGuardedRuntimeUpdateWithoutDatabaseConnection() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try (var factory = new MetadataSources(registry)
                .addAnnotatedClass(CrawlerMihomoPolicy.class).buildMetadata().buildSessionFactory()) {
            String query = CrawlerMihomoPolicyRepository.class
                    .getMethod("updateRuntime", CrawlerMihomoPolicy.class, long.class)
                    .getAnnotation(Query.class).value();
            AtomicInteger index = new AtomicInteger();
            String hql = java.util.regex.Pattern.compile(":#\\{#state\\.[^}]+}")
                    .matcher(query).replaceAll(match -> ":field" + index.incrementAndGet());
            assertNotNull(((SessionFactoryImplementor) factory).getQueryEngine()
                    .getHqlTranslator().translate(hql, null));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
