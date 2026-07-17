package com.example.platform.exceptionlogging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExceptionLoggingAutoConfiguration.class));

    @Test
    void configuresReporterAndUsesSpringApplicationName() {
        contextRunner.withPropertyValues("spring.application.name=orders-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExceptionReporter.class);
                    assertThat(context.getBean(ExceptionLoggingProperties.class).getApplicationName())
                            .isEqualTo("orders-service");
                });
    }

    @Test
    void canBeDisabled() {
        contextRunner.withPropertyValues("exception-logging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExceptionReporter.class));
    }
}
