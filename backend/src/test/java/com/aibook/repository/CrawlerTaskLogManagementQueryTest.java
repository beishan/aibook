package com.aibook.repository;

import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.util.Set;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

class CrawlerTaskLogManagementQueryTest {
    @Test
    void managementQueriesCompileAgainstPostgresEntityMappings() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
            configuration.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
        }
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            Set<String> names = Set.of("searchForManagement", "setRetained", "deleteSelected", "deleteExpired");
            for (Method method : CrawlerTaskLogRepository.class.getMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || !names.contains(method.getName())) continue;
                if (query.value().stripLeading().startsWith("delete") || query.value().stripLeading().startsWith("update")) {
                    session.createMutationQuery(query.value());
                } else {
                    session.createSelectionQuery(query.value());
                }
            }
            Set<String> activityQueries = Set.of("setRetained", "deleteSelected", "deleteUnretained", "deleteExpired");
            for (Method method : CrawlerSiteActivityRepository.class.getMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null && activityQueries.contains(method.getName())) {
                    session.createMutationQuery(query.value());
                }
            }
        }
    }
}
