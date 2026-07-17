package com.example.platform.exceptionlogging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExceptionLoggingAutoConfiguration.class));

    @Test
    void configuresReporterAndUsesSpringApplicationName() {
        contextRunner.withPropertyValues("spring.application.name=orders-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExceptionReporter.class);
                    assertThat(context).hasSingleBean(TraceContext.class);
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context).hasSingleBean(TracePropagationInterceptor.class);
                    assertThat(context).hasSingleBean(RestTemplateCustomizer.class);
                    assertThat(context).hasSingleBean(RestClientCustomizer.class);
                    assertThat(context.getBean(ExceptionLoggingProperties.class).getApplicationName())
                            .isEqualTo("orders-service");
                });
    }

    @Test
    void canBeDisabled() {
        contextRunner.withPropertyValues("exception-logging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExceptionReporter.class));
    }

    @Test
    void tracePropagationCanBeDisabledWithoutDisablingExceptionReporting() {
        contextRunner.withPropertyValues("exception-logging.trace-propagation-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExceptionReporter.class);
                    assertThat(context).hasSingleBean(TraceContext.class);
                    assertThat(context).doesNotHaveBean(TracePropagationInterceptor.class);
                    assertThat(context).doesNotHaveBean(RestTemplateCustomizer.class);
                    assertThat(context).doesNotHaveBean(RestClientCustomizer.class);
                });
    }
}
