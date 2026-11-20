package com.cie.competition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class YamlProfileConfigurationTest {
    @Test void defaultsToLocalYamlAndKeepsPrivateSettings() {
        var local = new YamlPropertiesFactoryBean();
        local.setResources(new ClassPathResource("application-local.yml"));
        var expected = local.getObject();
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            assertNull(context.getStartupFailure(), "Default YAML configuration should load");
            var environment = context.getEnvironment();
            assertEquals("local", environment.getDefaultProfiles()[0]);
            for (String key : java.util.List.of("spring.datasource.url", "spring.datasource.username", "spring.datasource.password", "jwt.secret"))
                assertTrue(Objects.equals(expected.getProperty(key), environment.getProperty(key)), "Local YAML must provide " + key);
            assertFalse(new ClassPathResource("config.properties").exists());
        });
    }

    @ParameterizedTest @ValueSource(strings = {"test", "prod"})
    void profilesUseTheirOwnEnvironmentAndDoNotLoadLocalCredentials(String profile) {
        String prefix = profile.toUpperCase(java.util.Locale.ROOT);
        Map<String, Object> variables = Map.of(prefix + "_DB_URL", "jdbc:mysql://127.0.0.1:3306/" + profile + "_fixture",
                prefix + "_DB_USERNAME", "fixture-user", prefix + "_DB_PASSWORD", "fixture-password",
                prefix + "_JWT_SECRET", "fixture-only-signing-key-with-more-than-32-bytes");
        new ApplicationContextRunner().withPropertyValues("spring.profiles.active=" + profile)
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture-environment", variables)))
                .withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
                    assertNull(context.getStartupFailure(), "Selected YAML profile should load");
                    var environment = context.getEnvironment();
                    for (var entry : Map.of("spring.datasource.url", "DB_URL", "spring.datasource.username", "DB_USERNAME",
                            "spring.datasource.password", "DB_PASSWORD", "jwt.secret", "JWT_SECRET").entrySet())
                        assertTrue(Objects.equals(variables.get(prefix + "_" + entry.getValue()), environment.getProperty(entry.getKey())),
                                "Selected profile must use its own variables for " + entry.getKey());
                    if (profile.equals("prod")) assertFalse(environment.getProperty("app.bootstrap-default-users.enabled", Boolean.class, true));
                    assertFalse(environment.getPropertySources().stream().anyMatch(source -> source.getName().contains("application-local.yml")));
                });
    }
}
