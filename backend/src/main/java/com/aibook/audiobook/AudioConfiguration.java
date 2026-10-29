package com.aibook.audiobook;

import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Audio stays in the existing process and database. */
@Configuration
public class AudioConfiguration {
    @Bean
    public Object audioSchema(JdbcTemplate jdbc, EntityManagerFactory entityManagerFactory) {
        new ResourceDatabasePopulator(new ClassPathResource("db/audiobook.sql"))
                .execute(jdbc.getDataSource());
        jdbc.update("UPDATE audio_import_tasks SET state = 'INTERRUPTED', "
                + "error = '服务重启，请重新探测' WHERE state = 'SCANNING'");
        return new Object();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService audioProbeExecutor() {
        // One worker, bounded admission is enforced by import task admission.
        return new java.util.concurrent.ThreadPoolExecutor(1, 1, 0, java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(2),
                Thread.ofPlatform().name("audio-probe").factory(),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    }
}
