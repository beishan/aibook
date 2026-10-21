package com.aibook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import com.aibook.model.entity.OperationLog;
import com.aibook.model.entity.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

class OperationLogRepositoryQueryTest {
    @Test
    void retentionFlagHasSafeLegacyDefaultAndUserRelationshipCannotCascadeDeletion() throws Exception {
        var column = OperationLog.class.getDeclaredField("retained")
                .getAnnotation(jakarta.persistence.Column.class);
        assertThat(column.nullable()).isFalse();
        assertThat(column.columnDefinition()).contains("default false");
        var relation = OperationLog.class.getDeclaredField("user")
                .getAnnotation(jakarta.persistence.ManyToOne.class);
        assertThat(relation.cascade()).isEmpty();
        assertThat(OperationLog.builder().build().isRetained()).isFalse();
    }

    @Test
    void destructiveQueriesAreOwnerScopedAndAutomaticCleanupExcludesRetainedRows() throws Exception {
        String expired = OperationLogRepository.class.getMethod("deleteExpired", User.class,
                OperationLog.Action.class, LocalDateTime.class).getAnnotation(Query.class).value();
        assertThat(expired).contains("l.user = :user", "l.action = :action",
                "coalesce(l.retained, false) = false", "l.createdAt < :cutoff")
                .doesNotContain("<=");
        String manual = OperationLogRepository.class.getMethod("deleteOwned", User.class, Long.class)
                .getAnnotation(Query.class).value();
        assertThat(manual).contains("l.id = :id", "l.user = :user").doesNotContain("retained");
        String retain = OperationLogRepository.class.getMethod("retain", User.class, Long.class)
                .getAnnotation(Query.class).value();
        assertThat(retain).contains("l.id = :id", "l.user = :user", "l.retained = true")
                .doesNotContain("retained = false");
        assertThat(OperationLogRetentionPolicyRepository.class.getMethod("findExpiringPolicyUserIds")
                .getAnnotation(Query.class).value()).contains("distinct", "p.retentionDays > 0");
    }

    @Test
    void newQueriesParseUsingActualEntityMappingsAndPostgresDialect() throws Exception {
        var registry = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false).build();
        try {
            var sources = new org.hibernate.boot.MetadataSources(registry);
            var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(jakarta.persistence.Entity.class));
            for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                String search = OperationLogRepository.class.getMethod("search", User.class,
                        OperationLog.Action.class, String.class, Pageable.class).getAnnotation(Query.class).value();
                assertThat(session.createQuery(search, OperationLog.class).getParameters())
                        .extracting(jakarta.persistence.Parameter::getName)
                        .containsExactlyInAnyOrder("user", "action", "pattern");
                for (var method : OperationLogRepository.class.getMethods()) {
                    Query annotation = method.getAnnotation(Query.class);
                    if (annotation != null && !method.getName().equals("search")) {
                        session.createMutationQuery(annotation.value());
                    }
                }
                String expiring = OperationLogRetentionPolicyRepository.class.getMethod("findExpiringPolicyUserIds")
                        .getAnnotation(Query.class).value();
                session.createQuery(expiring, Long.class);
            }
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
