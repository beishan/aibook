package com.aibook.repository;

import static org.assertj.core.api.Assertions.*;

import com.aibook.model.entity.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

class RecommendationMappingTest {
    @Test
    void schemaSupportsIndexedDatesAndCascadesRuleAndAccountDeletion() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false).build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(jakarta.persistence.Entity.class));
            for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            var metadata = sources.buildMetadata();
            var rules = metadata.getEntityBinding(RecommendationRule.class.getName()).getTable();
            var batches = metadata.getEntityBinding(RecommendationBatch.class.getName()).getTable();
            // Build the complete model to resolve inherited associations and physical foreign keys.
            try (var factory = metadata.buildSessionFactory()) {
                assertThat(rules.getForeignKeys().values()).anySatisfy(fk -> {
                    assertThat(fk.getReferencedTable().getName()).isEqualTo("users");
                    assertThat(fk.isCascadeDeleteEnabled()).isTrue();
                });
                assertThat(batches.getForeignKeys().values()).anySatisfy(fk -> {
                    assertThat(fk.getReferencedTable().getName()).isEqualTo("recommendation_rules");
                    assertThat(fk.isCascadeDeleteEnabled()).isTrue();
                });
                assertThat(batches.getIndexes().values()).anySatisfy(index ->
                        assertThat(index.getColumns()).extracting("name")
                                .contains("rule_id", "recommendation_date", "id"));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
