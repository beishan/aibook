package com.aibook.repository;

import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class BookListPageQueryTest {
    @Test
    void bookListPaginationQueryCompilesWithPostgresMappings() throws Exception {
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
            Query query = BookRepository.class.getMethod("findBookListPage", Long.class,
                    com.aibook.model.entity.User.class, Pageable.class).getAnnotation(Query.class);
            session.createSelectionQuery(query.value());
        }
    }
}
