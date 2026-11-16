package com.navan.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) throws IOException {
        ensureParentDirectory(properties.getUrl());
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("navan-sqlite");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(15_000);
        return dataSource;
    }

    static void ensureParentDirectory(String jdbcUrl) throws IOException {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            return;
        }
        String spec = jdbcUrl.substring("jdbc:sqlite:".length());
        int query = spec.indexOf('?');
        if (query >= 0) {
            spec = spec.substring(0, query);
        }
        if (spec.startsWith("file:")) {
            spec = spec.substring("file:".length());
        }
        if (spec.isBlank() || ":memory:".equals(spec)) {
            return;
        }
        Path parent = Path.of(spec).toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
