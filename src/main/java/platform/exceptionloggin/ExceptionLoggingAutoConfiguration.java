package platform.exceptionloggin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionLoggingProperties.class)
@ConditionalOnProperty(prefix = "exception-logging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionLoggingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ObjectMapper exceptionLoggingObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    ContextSanitizer exceptionLoggingContextSanitizer(ObjectMapper objectMapper,
                                                      ExceptionLoggingProperties properties) {
        return new ContextSanitizer(objectMapper, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ExceptionClassifier exceptionClassifier() {
        return new DefaultExceptionClassifier();
    }

    @Bean
    @ConditionalOnMissingBean
    TraceIdGenerator traceIdGenerator() {
        return new UuidTraceIdGenerator();
    }

    @Bean
    @ConditionalOnMissingBean
    TraceContext traceContext(TraceIdGenerator generator) {
        return new DefaultTraceContext(generator);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "exception-logging", name = "trace-propagation-enabled",
            havingValue = "true", matchIfMissing = true)
    TracePropagationInterceptor tracePropagationInterceptor(TraceContext traceContext,
                                                            ExceptionLoggingProperties properties) {
        return new TracePropagationInterceptor(traceContext, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "exception-logging", name = "trace-propagation-enabled",
            havingValue = "true", matchIfMissing = true)
    RestTemplateCustomizer exceptionLoggingRestTemplateCustomizer(TracePropagationInterceptor interceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "exception-logging", name = "trace-propagation-enabled",
            havingValue = "true", matchIfMissing = true)
    RestClientCustomizer exceptionLoggingRestClientCustomizer(TracePropagationInterceptor interceptor) {
        return builder -> builder.requestInterceptor(interceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    ReportedExceptionRegistry reportedExceptionRegistry() {
        return new ReportedExceptionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    ExceptionReporter exceptionReporter(ExceptionLoggingProperties properties,
                                        ExceptionClassifier classifier,
                                        ObjectMapper objectMapper,
                                        ContextSanitizer sanitizer,
                                        ReportedExceptionRegistry registry,
                                        TraceContext traceContext,
                                        Environment environment) {
        if (properties.getApplicationName() == null || properties.getApplicationName().isBlank()) {
            properties.setApplicationName(environment.getProperty("spring.application.name", "unknown-service"));
        }
        return new Slf4jExceptionReporter(properties, classifier, objectMapper, sanitizer, registry, traceContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "exception-logging", name = "aspect-enabled", havingValue = "true", matchIfMissing = true)
    LogFailureAspect logFailureAspect(ExceptionReporter reporter) {
        return new LogFailureAspect(reporter);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"org.springframework.web.servlet.DispatcherServlet", "jakarta.servlet.Filter"})
    static class ServletConfiguration {
        @Bean
        @ConditionalOnProperty(prefix = "exception-logging", name = "trace-propagation-enabled",
                havingValue = "true", matchIfMissing = true)
        TracePropagationFilter tracePropagationFilter(TraceContext traceContext,
                                                      ExceptionLoggingProperties properties) {
            return new TracePropagationFilter(traceContext, properties);
        }

        @Bean
        @ConditionalOnProperty(prefix = "exception-logging", name = "web-handler-enabled", havingValue = "true")
        GlobalExceptionHandler globalExceptionHandler(ExceptionReporter reporter,
                                                      ExceptionClassifier classifier,
                                                      ContextSanitizer sanitizer,
                                                      TraceContext traceContext) {
            return new GlobalExceptionHandler(reporter, classifier, sanitizer, traceContext);
        }
    }
}
