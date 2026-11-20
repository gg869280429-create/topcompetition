package com.cie.competition;

import com.cie.competition.common.config.DataInitializer;
import com.cie.competition.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProductionBootstrapTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DataInitializer.class)
            .withBean(UserMapper.class, () -> mock(UserMapper.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));

    @Test
    void disabledBootstrapDoesNotCreateDefaultAccounts() {
        runner.withPropertyValues("app.bootstrap-default-users.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DataInitializer.class);
            verifyNoInteractions(context.getBean(UserMapper.class));
        });
    }

    @Test
    void localBootstrapRemainsEnabledByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(DataInitializer.class));
    }
}
