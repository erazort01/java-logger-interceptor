# Java Logger Interceptor

[Versión en español](README.md)

Generic Java library for consistent exception handling, structured logging, and trace propagation in any Spring Boot microservice. It classifies database, business, connectivity, and unexpected failures; records the microservice, correlation data, table, operation, and root cause; and places the serialized failure object under `metadata.failedObject` within safe limits.

The failure object, custom metadata, exception messages, and stack trace always pass through mandatory masking. Built-in masking rules cannot be disabled or replaced through configuration. Services may only add domain-specific sensitive field names.

## Main capabilities

- Spring Boot auto-configuration after adding one dependency.
- Trace-ID generation, reuse, and propagation across microservices.
- Extensible exception classification through `ExceptionClassifier`.
- Structured JSON event emitted by the `exception.audit` logger.
- `BusinessException` with a stable business code and HTTP status.
- Opt-in uniform HTTP error responses without exposing internal technical details.
- `@LogFailure` for table, operation, and failure-object selection.
- Programmatic `ExceptionReporter` for jobs, listeners, consumers, and asynchronous flows.
- Mandatory recursive masking for metadata, messages, and stack traces.
- Duplicate protection for the same exception instance.

## Current status

Stable release for Java 17 and Spring Boot 3.5.x, prepared for GitHub Packages as `io.github.erazort01:java-logger-interceptor:1.0.0`. The project is distributed under the Apache License 2.0; validate it with representative services before production adoption.

## Installation

Publishing a GitHub Release with a tag that matches the POM version, such as `v1.0.0`, runs `Publish Java Package with Maven`. The workflow uses only the ephemeral `GITHUB_TOKEN`. Then add:

```xml
<dependency>
    <groupId>io.github.erazort01</groupId>
    <artifactId>java-logger-interceptor</artifactId>
    <version>1.0.0</version>
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
        "Internal diagnostic detail",
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
  web-handler-enabled: false
  aspect-enabled: true
  trace-propagation-enabled: true
  accept-incoming-trace-ids: false
  trace-header-name: X-Trace-Id
  correlation-header-name: X-Correlation-Id
  trace-propagation-allowed-hosts:
    - inventory.internal
    - payments.internal
  include-stacktrace: false
  max-message-length: 4096
  max-stack-trace-length: 32768
  max-metadata-depth: 8
  max-metadata-nodes: 1000
  additional-sensitive-fields:
    - internalReference
    - legacyCredential
```

`additional-sensitive-fields` extends the mandatory internal rules. Text length, metadata depth, and metadata node count are bounded before logging. There is no configuration property that disables masking or removes built-in rules.

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
    "failedObjectType": "platform.demo.Record",
    "failedObject": {
      "id": 42,
      "ownerName": "[REDACTED]",
      "token": "[REDACTED]"
    }
  },
  "stackTrace": "java.lang.IllegalStateException: ..."
}
```

The library generates an internal ID for each inbound request, stores it in MDC, and returns it in the response. It only trusts an inbound ID when `accept-incoming-trace-ids=true`, which should be reserved for ingress protected by a trusted proxy. Spring Boot-built `RestClient` and `RestTemplate` instances only propagate the trace to exact hosts in `trace-propagation-allowed-hosts`; an empty list propagates nowhere. `X-Correlation-Id` remains aligned with the trace ID.

## Consuming from GitHub Packages

GitHub Packages requires authentication even for public Maven packages. Consumers must configure a `github` server in `~/.m2/settings.xml` with their GitHub username and a classic PAT scoped to `read:packages`, and declare `https://maven.pkg.github.com/erazort01/java-logger-interceptor` as a repository. No credentials are stored in this project.

For jobs, consumers, and asynchronous work:

```java
try (TraceScope scope = traceContext.open()) {
    executor.execute(traceContext.wrap(() -> process(command)));
}
```

Closing a scope restores the previous MDC values so pooled threads cannot leak a trace into unrelated work.

## Mandatory masking

Built-in rules cover common credentials, tokens, keys, personal identifiers, names, contact details, addresses, and dates of birth. The library also masks e-mail addresses, JWTs, Bearer credentials, and credential assignments found in free text.

Automatic detection cannot understand every possible business field. Each service must add domain-specific names through `additional-sensitive-fields` and review selected failure objects before production rollout. Stack traces and the built-in HTTP advice are disabled by default. Legacy `BusinessException` constructors treat `message` as internal detail; use the three-string constructor to provide an explicit public message.

## Verification

```bash
mvn clean verify
```

## License

Distributed under the [Apache License 2.0](LICENSE).

See [USAGE_GUIDE.md](USAGE_GUIDE.md) for the detailed English integration guide, [GUIA_DE_USO.md](GUIA_DE_USO.md) for Spanish, [ARQUITECTURA.md](ARQUITECTURA.md) for technical decisions, and [security_best_practices_report.md](security_best_practices_report.md) for the security audit.
