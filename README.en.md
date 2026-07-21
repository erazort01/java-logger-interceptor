# Java Logger Interceptor

[Versión en español](README.md)

Generic Java library for consistent exception handling, structured logging, and trace propagation in any Spring Boot microservice. It classifies authentication (`AUTH`), database, business, connectivity, and unexpected failures; records the microservice, correlation data, table, operation, and root cause; and places the complete serialized failure object under `metadata.failedObject`.

The failure object, custom metadata, exception messages, and stack trace always pass through mandatory masking. Built-in masking rules cannot be disabled or replaced through configuration. Services may only add domain-specific sensitive field names.

## Main capabilities

- Spring Boot auto-configuration after adding one dependency.
- Trace-ID generation, reuse, and propagation across microservices.
- Extensible exception classification through `ExceptionClassifier`.
- `AUTH` classification for Spring Security and JAAS authentication exceptions and HTTP 401 failures, distinct from 403 authorization failures.
- Structured JSON event emitted by the `exception.audit` logger.
- `BusinessException` with a stable business code and HTTP status.
- Uniform HTTP error responses without exposing internal technical details.
- `@LogFailure` for table, operation, and failure-object selection.
- Programmatic `ExceptionReporter` for jobs, listeners, consumers, and asynchronous flows.
- Mandatory recursive masking for metadata, messages, and stack traces.
- Duplicate protection for the same exception instance.

## Current status

Functional MVP for Java 17 and Spring Boot 3.x. Before production adoption, replace the example Maven coordinates, publish an immutable release to the selected repository, and validate it with representative services.

## Installation

Publish the artifact to Nexus, Artifactory, or another internal Maven repository, then add:

```xml
<dependency>
    <groupId>com.example.platform</groupId>
    <artifactId>java-logger-interceptor</artifactId>
    <version>0.1.0</version>
</dependency>
```

The microservice name is automatically read from `spring.application.name`.

## Basic usage

```java
@LogFailure(table = "example_records", operation = "INSERT", captureArgument = 0)
public ExampleRecord save(ExampleRecord record) {
    return repository.save(record);
}
```

If the method fails, the complete serialized `record` is stored under `metadata.failedObject`. Sensitive fields and sensitive free-text patterns are replaced with `[REDACTED]` before the event is written.

For non-AOP flows:

```java
try {
    remoteClient.execute(command);
} catch (RuntimeException error) {
    exceptionReporter.report(error, FailureContext.builder()
            .operation("EXECUTE_REMOTE_ACTION")
            .failedObject(command)
            .metadata("targetService", "remote-service")
            .build());
    throw error;
}
```

For business rules:

```java
throw new BusinessException(
        "RESOURCE_STATE_INVALID",
        "The resource is not in a valid state"
);
```

## Configuration

```yaml
spring:
  application:
    name: example-service

exception-logging:
  enabled: true
  web-handler-enabled: true
  aspect-enabled: true
  trace-propagation-enabled: true
  trace-header-name: X-Trace-Id
  correlation-header-name: X-Correlation-Id
  include-stacktrace: true
  additional-sensitive-fields:
    - internalReference
    - legacyCredential
```

`additional-sensitive-fields` extends the mandatory internal rules. There is no configuration property that disables masking or removes built-in rules.

## Event example

```json
{
  "microservice": "example-service",
  "category": "DATABASE",
  "exceptionType": "org.springframework.dao.DataIntegrityViolationException",
  "message": "duplicate key",
  "rootCause": "duplicate key value violates unique constraint",
  "table": "example_records",
  "operation": "INSERT",
  "correlationId": "req-7f8a",
  "traceId": "a4c31d",
  "metadata": {
    "method": "ExampleService.save(..)",
    "failedObjectType": "com.example.ExampleRecord",
    "failedObject": {
      "id": 42,
      "ownerName": "[REDACTED]",
      "token": "[REDACTED]"
    }
  },
  "stackTrace": "java.lang.IllegalStateException: ..."
}
```

The library generates an ID when an inbound request has none, reuses a valid `X-Trace-Id`, stores it in MDC, returns it in the response, and propagates it through Spring Boot-built `RestClient` and `RestTemplate` instances. `X-Correlation-Id` is supported as a legacy fallback and kept aligned with the trace ID.

For jobs, consumers, and asynchronous work:

```java
try (TraceScope scope = traceContext.open()) {
    executor.execute(traceContext.wrap(() -> process(command)));
}
```

Closing a scope restores the previous MDC values so pooled threads cannot leak a trace into unrelated work.

## Mandatory masking

Built-in rules cover common credentials, tokens, keys, personal identifiers, names, contact details, addresses, and dates of birth. The library also masks e-mail addresses, JWTs, Bearer credentials, and credential assignments found in free text.

Automatic detection cannot understand every possible business field. Each service must add domain-specific names through `additional-sensitive-fields` and review selected failure objects before production rollout.

## Verification

```bash
mvn clean verify
```

See [USAGE_GUIDE.md](USAGE_GUIDE.md) for the detailed English integration guide, [GUIA_DE_USO.md](GUIA_DE_USO.md) for Spanish, and [ARQUITECTURA.md](ARQUITECTURA.md) for technical decisions.
