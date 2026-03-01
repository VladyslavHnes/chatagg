package com.chatagg.db;

import com.chatagg.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final HikariDataSource dataSource;

    public DatabaseManager(AppConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.dbUser);
        hikariConfig.setPassword(config.dbPassword);
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);

        this.dataSource = new HikariDataSource(hikariConfig);

        runMigrations(config);
    }

    private void runMigrations(AppConfig config) {
        log.info("Running Flyway migrations...");
        Flyway flyway = Flyway.configure()
                .dataSource(config.jdbcUrl(), config.dbUser, config.dbPassword)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        log.info("Flyway migrations complete.");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
