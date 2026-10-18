package com.tradepulse.tradepulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonPersistenceConfig {
    @Bean
    HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper mapper) {
        // Keep JSONB collections identical to REST collections instead of discovering unrelated classpath modules.
        return properties -> properties.put("hibernate.type.json_format_mapper", new JacksonJsonFormatMapper(mapper));
    }
}
