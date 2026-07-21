# Usage guide

[Guía de uso en español](GUIA_DE_USO.md)

This guide explains how to integrate `java-logger-interceptor` into any Spring Boot microservice and use it for database operations, business rules, remote calls, scheduled jobs, and message consumers.

## 1. Requirements

- Java 17 or later.
- Spring Boot 3.5.x.
- The library available from a Maven repository accessible to the service.
- A unique `spring.application.name` for each microservice.

## 2. Add the dependency

```xml
<dependency>
    <groupId>io.github.erazort01</groupId>
    <artifactId>java-logger-interceptor</artifactId>
    <version>1.0.0</version>
</dependency>
```

The starter is automatically configured. No component scan or manual configuration import is required.
The Maven repository is `https://maven.pkg.github.com/erazort01/java-logger-interceptor`. Consumers configure a `github` server in `~/.m2/settings.xml` with a classic PAT scoped to `read:packages`; credentials never belong in the POM.

## 3. Configure the microservice

```yaml
spring:
  application:
    name: example-service

exception-logging:
  enabled: true
  web-handler-enabled: false
  aspect-enabled: true
  trace-propagation-enabled: true
  accept-incoming-trace-ids: false
  trace-header-name: X-Trace-Id
  correlation-header-name: X-Correlation-Id
  trace-propagation-allowed-hosts:
    - remote-service.internal
  include-stacktrace: false
  max-message-length: 4096
  max-stack-trace-length: 32768
  max-metadata-depth: 8
  max-metadata-nodes: 1000
  additional-sensitive-fields:
    - internalReference
    - legacyCredential
```

| Property | Default | Purpose |
|---|---:|---|
| `enabled` | `true` | Enables or disables the library itself. |
| `application-name` | `spring.application.name` | Overrides the name recorded in events. |
| `web-handler-enabled` | `false` | Explicitly enables uniform HTTP error responses. |
| `aspect-enabled` | `true` | Enables `@LogFailure` interception. |
| `trace-propagation-enabled` | `true` | Generates and propagates the HTTP trace context. |
| `accept-incoming-trace-ids` | `false` | Trusts external IDs; enable only behind a trusted proxy. |
| `trace-header-name` | `X-Trace-Id` | Canonical trace-ID header. |
| `correlation-header-name` | `X-Correlation-Id` | Supported legacy correlation header. |
| `trace-propagation-allowed-hosts` | empty | Exact hosts to which clients may propagate IDs. |
| `include-stacktrace` | `false` | Adds a sanitized, bounded stack trace to the structured event. |
| `max-message-length` | `4096` | Bounds each text value before sanitization. |
| `max-stack-trace-length` | `32768` | Bounds stack traces when enabled. |
| `max-metadata-depth` | `8` | Bounds metadata nesting. |
| `max-metadata-nodes` | `1000` | Bounds the total sanitized metadata nodes. |
| `additional-sensitive-fields` | empty | Extends mandatory masking with domain-specific field names. |

Masking has no disable switch. Built-in sensitive rules cannot be replaced or removed.

## 4. Database operations

Annotate the service method where the application knows the table, operation, and relevant object:

```java
@LogFailure(
        table = "example_records",
        operation = "INSERT",
        captureArgument = 0
)
public ExampleRecord save(ExampleRecord record) {
    return repository.save(record);
}
```

`captureArgument` is zero-based. `0` selects the first method argument, `1` the second, and `-1` selects no argument. If a Spring `DataAccessException` or `SQLException` occurs, the event category is `DATABASE`.

The table is explicit because parsing SQL or driver messages would not be reliable across databases and versions.

Spring AOP only intercepts calls that pass through a Spring-managed proxy. Self-invocation inside the same bean does not trigger the annotation; use another bean or `ExceptionReporter` in that case.

## 5. Business exceptions

```java
throw new BusinessException(
        "RESOURCE_STATE_INVALID",
        "Internal state was incompatible",
        "The resource is not in a valid state"
);
```

The default HTTP status is `422 Unprocessable Entity`. A custom status can be supplied:
The second argument is internal log detail and the third is the only client-facing message. Legacy constructors without an explicit public message return the safe generic message.

```java
throw new BusinessException(
        "RESOURCE_NOT_FOUND",
        "Internal resource identifier was not found",
        "The resource was not found",
        HttpStatus.NOT_FOUND
);
```

Business codes should remain stable even if the human-readable message changes or is translated.

## 6. Connectivity failures

The default classifier recognizes common Spring client, connection, socket, and timeout exceptions as `CONNECTIVITY`.

```java
@LogFailure(operation = "GET_REMOTE_RESOURCE", captureArgument = 0)
public RemoteResource findRemoteResource(UUID resourceId) {
    return remoteClient.getResource(resourceId);
}
```

When handled by the built-in HTTP advice, database and connectivity failures return `503 Service Unavailable` with the public code `DEPENDENCY_UNAVAILABLE`. Technical details stay in the log.

## 7. Programmatic reporting

Use the programmatic API for jobs, consumers, listeners, or dynamic context:

```java
@Service
public class RemoteActionProcessor {
    private final ExceptionReporter exceptionReporter;

    public RemoteActionProcessor(ExceptionReporter exceptionReporter) {
        this.exceptionReporter = exceptionReporter;
    }

    public void process(ActionCommand command) {
        try {
            remoteClient.execute(command);
        } catch (RuntimeException error) {
            exceptionReporter.report(error, FailureContext.builder()
                    .operation("EXECUTE_REMOTE_ACTION")
                    .failedObject(command)
                    .metadata("targetService", "remote-service")
                    .metadata("requestId", command.requestId())
                    .build());
            throw error;
        }
    }
}
```

The reporter records the failure; it does not decide transaction rollback, retries, dead-letter handling, or message acknowledgement.

## 8. Failure object and metadata

When `failedObject` is provided, its complete Jackson-serializable representation is placed at:

```text
metadata.failedObject
```

The Java type is placed at:

```text
metadata.failedObjectType
```

Custom metadata remains next to those fields. The entire metadata tree is recursively sanitized before serialization. Fields ignored by the configured Jackson mapper remain ignored, and objects that cannot be serialized are represented by an `UNSERIALIZABLE` marker.

Select objects deliberately. Full objects can produce large log events and may contain domain-specific sensitive values that require additional field rules.

## 9. Mandatory sensitive-data masking

The library always masks built-in field families such as:

- Passwords, secrets, credentials, tokens, authorization values, and keys.
- Personal identifiers, contact details, and access codes.
- Tax, identity, and passport identifiers.
- Names, e-mail addresses, phone numbers, addresses, and birth dates.

It also detects common sensitive patterns inside arbitrary text, including e-mails, JWTs, Bearer credentials, and assignments such as `password=...`.

Add domain-specific names without weakening defaults:

```yaml
exception-logging:
  additional-sensitive-fields:
    - internalAlias
    - policyHolderReference
    - legacyCredential
```

Automatic masking reduces risk but cannot infer every possible business meaning. Security and privacy review remains required before selecting production objects.

## 10. Correlation

For inbound HTTP requests, the library ignores external trace headers by default and generates a UUID. With `accept-incoming-trace-ids=true`, it reuses a valid `X-Trace-Id` or legacy `X-Correlation-Id`; only enable this behind a trusted proxy. The selected value is stored as `traceId` and `correlationId` in MDC, returned in both response headers, and removed when the request scope closes.

Inbound values must contain 8–128 letters, digits, dots, underscores, or hyphens. Unsafe values are rejected and replaced.

Spring Boot-built `RestClient` and `RestTemplate` instances send the current ID only to exact hosts configured in `trace-propagation-allowed-hosts`:

```java
public RemoteServiceGateway(RestClient.Builder builder) {
    this.restClient = builder.baseUrl("http://remote-service").build();
}
```

Raw clients created outside the Spring Boot builders do not receive customizers. Feign, WebClient, messaging clients, and other transports can read `TraceContext.currentTraceId()` from their own interceptor.

For a job or message consumer, create or reuse a scope:

```java
try (TraceScope scope = traceContext.open(message.traceId())) {
    process(message);
}
```

Use `scope.traceId()` as the outgoing message header. For thread-pool work, capture the context before dispatch:

```java
executor.execute(traceContext.wrap(() -> process(command)));
```

The wrapper restores the worker thread's previous MDC state after completion.

## 11. HTTP handling

The built-in advice is disabled by default and has the lowest precedence when explicitly enabled.

| Failure | Status | Public code |
|---|---:|---|
| Authentication | 401 | `AUTHENTICATION_FAILED` |
| `BusinessException` | Configured 4xx status; 422 by default | Validated code and explicit public message, or a generic message |
| Database or connectivity | 503 | `DEPENDENCY_UNAVAILABLE` |
| Unexpected | 500 | `INTERNAL_ERROR` |

Authentication covers Spring Security and JAAS authentication exceptions and HTTP 401 failures. Authorization failures such as HTTP 403 are intentionally not classified as `AUTH`. The public 401 response uses a generic message and never exposes the original authentication error.

If the service already owns a `@RestControllerAdvice`, disable only the library advice:

```yaml
exception-logging:
  web-handler-enabled: false
```

Masking and the reporter remain active.

## 12. Custom classification

Provide an `ExceptionClassifier` bean. Auto-configuration will back off:

```java
@Bean
ExceptionClassifier exceptionClassifier() {
    DefaultExceptionClassifier defaults = new DefaultExceptionClassifier();
    return error -> containsLegacyBusinessCause(error)
            ? ErrorCategory.BUSINESS
            : defaults.classify(error);
}
```

A service may also provide its own `ExceptionReporter` bean if it needs a custom event transport or additional fields.

## 13. Troubleshooting

- `unknown-service`: configure `spring.application.name`.
- `table` is null: supply it through `@LogFailure` or `FailureContext`.
- `metadata.failedObject` is missing: select a valid `captureArgument` or call `.failedObject(...)`.
- Annotation does not run: check that AOP is enabled, the class is a Spring bean, and the call is not self-invocation.
- Advice conflict: set `web-handler-enabled=false`.
- A domain-sensitive value is visible: add its field name to `additional-sensitive-fields` and review similar domain fields.

## 14. Integration checklist

- [ ] The dependency comes from the Maven repository configured for the service.
- [ ] `spring.application.name` uniquely identifies the service.
- [ ] The service has chosen either the built-in or its own HTTP advice.
- [ ] Critical operations declare table and operation explicitly.
- [ ] Selected failure objects are necessary and privacy-reviewed.
- [ ] Domain-specific names are in `additional-sensitive-fields`.
- [ ] MDC contains correlation and trace identifiers.
- [ ] `exception.audit` events reach the logging platform.
- [ ] Business, database, connectivity, and unexpected failures have been tested.
- [ ] Event volume, retention, dashboards, and alerts have been reviewed.

## 15. Verification

Run the library test suite:

```bash
mvn clean verify
```

For each consuming service, trigger one controlled failure and verify that the complete object appears under `metadata.failedObject`, all expected sensitive fields are `[REDACTED]`, correlation values are present, and the API response contains no internal technical detail.
