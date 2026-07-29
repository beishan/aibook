package com.aibook.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

/**
 * Jackson 配置
 * 解决 Hibernate 懒加载代理序列化问题
 */
@Configuration
public class JacksonConfig {

    @Value("${spring.jackson.time-zone:Asia/Shanghai}")
    private String timeZone;

    @Bean
    public ObjectMapper objectMapper() {
        // 创建 Hibernate5 模块
        Hibernate5JakartaModule hibernateModule = new Hibernate5JakartaModule();
        // 配置为不序列化未加载的懒加载属性
        hibernateModule.configure(Hibernate5JakartaModule.Feature.FORCE_LAZY_LOADING, false);

        return Jackson2ObjectMapperBuilder.json()
                .modules(hibernateModule, new JavaTimeModule())
                .timeZone(TimeZone.getTimeZone(timeZone))
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
