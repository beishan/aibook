package com.aibook.repository;

import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.util.Set;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

/** Validate new HQL against real entity mappings without requiring a live database. */
class CrawlerBlacklistScreenQueryTest {
    @Test
    void screeningQueriesCompileAgainstPostgresEntityMappings() throws Exception {
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
            Set<String> methods = Set.of("blacklistScreenUpperId", "countBlacklistScreenCandidates",
                    "blacklistScreenCandidates", "blacklistAllScreenResults", "matchedIds", "interruptUnfinished", "latestForScope");
            for (Class<?> repository : new Class<?>[] {CrawlerBookRepository.class,
                    CrawlerBlacklistScreenResultRepository.class, CrawlerBlacklistScreenJobRepository.class}) {
                for (Method method : repository.getMethods()) {
                    Query query = method.getAnnotation(Query.class);
                    if (query == null || !methods.contains(method.getName())) continue;
                    if (query.value().startsWith("update")) session.createMutationQuery(query.value());
                    else session.createSelectionQuery(query.value());
                }
            }
        }
    }
}
