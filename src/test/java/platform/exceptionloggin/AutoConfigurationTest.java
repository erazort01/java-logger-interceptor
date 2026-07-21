package platform.exceptionloggin;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        contextRunner.withPropertyValues("spring.application.name=example-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExceptionReporter.class);
                    assertThat(context).hasSingleBean(TraceContext.class);
                    assertThat(context).hasSingleBean(TraceIdGenerator.class);
                    assertThat(context).hasSingleBean(TracePropagationInterceptor.class);
                    assertThat(context).hasSingleBean(RestTemplateCustomizer.class);
                    assertThat(context).hasSingleBean(RestClientCustomizer.class);
                    assertThat(context.getBean(ExceptionLoggingProperties.class).getApplicationName())
                            .isEqualTo("example-service");
                    assertThat(context.getBean(ExceptionLoggingProperties.class).isWebHandlerEnabled()).isFalse();
                    assertThat(context.getBean(ExceptionLoggingProperties.class).isIncludeStacktrace()).isFalse();
                    assertThat(context.getBean(ExceptionLoggingProperties.class).isAcceptIncomingTraceIds()).isFalse();
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

    @Test
    void backsOffWithoutReplacingTheConsumersObjectMapper() {
        ObjectMapper consumerMapper = new ObjectMapper();

        contextRunner.withBean(ObjectMapper.class, () -> consumerMapper)
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(context.getBean(ObjectMapper.class)).isSameAs(consumerMapper);
                });
    }
}
