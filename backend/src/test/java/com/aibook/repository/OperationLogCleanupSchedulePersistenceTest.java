package com.aibook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aibook.model.entity.OperationLogCleanupSchedule;
import com.aibook.model.entity.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

class OperationLogCleanupSchedulePersistenceTest {

    @Test
    void firstRepositorySaveMergesAssignedAccountIdAndFlushesInsert() throws Exception {
        verifyRepositorySave(true);
    }

    @Test
    void scheduleCanBeCreatedWithOnlyAccountIdLikeServiceAndScheduler() throws Exception {
        verifyRepositorySave(false);
    }

    private void verifyRepositorySave(boolean includeUser) throws Exception {
        List<String> mutations = new ArrayList<>();
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet result = mock(ResultSet.class);
            // The schedule does not exist yet. Exercise Hibernate's actual transient merge path.
            when(statement.executeQuery()).thenReturn(result);
            when(statement.executeUpdate()).thenAnswer(call -> {
                mutations.add(sql);
                return 1;
            });
            return statement;
        });
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenReturn(connection);
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", false)
                .addService(ConnectionProvider.class, provider).build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(jakarta.persistence.Entity.class));
            for (var entity : scanner.findCandidateComponents("com.aibook.model.entity")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory();
                    var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                User account = includeUser ? session.getReference(User.class, 42L) : null;
                var repository = new SimpleJpaRepository<>(OperationLogCleanupSchedule.class, session);
                var schedule = OperationLogCleanupSchedule.builder().userId(42L).user(account)
                        .frequency(OperationLogCleanupSchedule.Frequency.DAILY)
                        .nextRunAt(LocalDateTime.of(2026, 10, 22, 3, 35)).build();
                var saved = repository.saveAndFlush(schedule);
                assertThat(saved.getUserId()).isEqualTo(42L);
                assertThat(session.contains(saved)).isTrue();
                assertThat(mutations).anySatisfy(sql -> assertThat(sql)
                        .startsWith("insert into operation_log_cleanup_schedules"));
                saved.setEnabled(false);
                saved.setNextRunAt(null);
                repository.saveAndFlush(saved);
                assertThat(mutations).anySatisfy(sql -> assertThat(sql)
                        .startsWith("update operation_log_cleanup_schedules"));
                transaction.rollback();
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
