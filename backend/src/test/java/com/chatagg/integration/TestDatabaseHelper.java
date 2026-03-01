package com.chatagg.integration;

import com.chatagg.config.AppConfig;
import com.chatagg.db.DatabaseManager;
import org.testcontainers.containers.PostgreSQLContainer;

public class TestDatabaseHelper {

    public static AppConfig configFrom(PostgreSQLContainer<?> pg) {
        AppConfig config = new AppConfig();
        config.dbHost = pg.getHost();
        config.dbPort = pg.getFirstMappedPort();
        config.dbName = pg.getDatabaseName();
        config.dbUser = pg.getUsername();
        config.dbPassword = pg.getPassword();
        return config;
    }

    public static DatabaseManager dbFrom(PostgreSQLContainer<?> pg) {
        return new DatabaseManager(configFrom(pg));
    }
}
