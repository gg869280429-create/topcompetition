package com.cie.competition;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;

abstract class IsolatedDatabaseTest {
    private static String databaseUrl;
    private static Properties settings;

    @DynamicPropertySource
    static synchronized void isolatedDatabase(DynamicPropertyRegistry registry) throws Exception {
        if (databaseUrl == null) {
            var yaml = new YamlPropertiesFactoryBean();
            var local = new ClassPathResource("application-local.yml");
            yaml.setResources(local.exists() ? new org.springframework.core.io.Resource[]{new ClassPathResource("application.yml"), local} : new org.springframework.core.io.Resource[]{new ClassPathResource("application.yml")});
            settings = yaml.getObject();
            var environment = new org.springframework.core.env.StandardEnvironment();
            for (String key : java.util.List.of("url", "username", "password")) {
                String envKey = java.util.Map.of("url", "DB_URL", "username", "DB_USERNAME", "password", "DB_PASSWORD").get(key);
                settings.setProperty("spring.datasource." + key, environment.getProperty(envKey, environment.resolveRequiredPlaceholders(settings.getProperty("spring.datasource." + key))));
            }
            settings.setProperty("jwt.secret", environment.getProperty("JWT_SECRET", environment.resolveRequiredPlaceholders(settings.getProperty("jwt.secret"))));
            String configuredUrl = settings.getProperty("spring.datasource.url");
            int query = configuredUrl.indexOf('?');
            String base = query < 0 ? configuredUrl : configuredUrl.substring(0, query);
            String options = query < 0 ? "" : configuredUrl.substring(query);
            String server = base.substring(0, base.lastIndexOf('/') + 1);
            String database = "competition_test_" + UUID.randomUUID().toString().replace("-", "");
            databaseUrl = server + database + options;
            String username = settings.getProperty("spring.datasource.username");
            String password = settings.getProperty("spring.datasource.password");
            try (var connection = DriverManager.getConnection(server + options, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
            Path sqlRoot = Path.of("../sql").toAbsolutePath().normalize();
            try (var connection = DriverManager.getConnection(databaseUrl, username, password)) {
                String init = Files.readString(sqlRoot.resolve("init.sql"), StandardCharsets.UTF_8)
                        .replaceAll("(?im)^CREATE DATABASE[^;]*;", "")
                        .replaceAll("(?im)^USE\\s+[^;]*;", "");
                ScriptUtils.executeSqlScript(connection, new EncodedResource(
                        new ByteArrayResource(init.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            }
            System.out.println("独立测试库：" + database);
        }
        registry.add("spring.datasource.url", () -> databaseUrl);
        registry.add("spring.datasource.username", () -> settings.getProperty("spring.datasource.username"));
        registry.add("spring.datasource.password", () -> settings.getProperty("spring.datasource.password"));
        registry.add("jwt.secret", () -> settings.getProperty("jwt.secret"));
    }
}
