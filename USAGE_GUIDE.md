# Usage guide

[Guía de uso en español](GUIA_DE_USO.md)

This guide explains how to integrate `exception-logging-spring-boot-starter` into a Spring Boot microservice and use it for database operations, business rules, remote calls, scheduled jobs, and message consumers.

## 1. Requirements

- Java 17 or later.
- Spring Boot 3.x.
- The library published in the corporate Maven repository.
- A unique `spring.application.name` for each microservice.

## 2. Add the dependency

```xml
<dependency>
    <groupId>com.example.platform</groupId>
    <artifactId>exception-logging-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The starter is automatically configured. No component scan or manual configuration import is required.

## 3. Configure the microservice

```yaml
spring:
  application:
    name: orders-service

exception-logging:
  enabled: true
  web-handler-enabled: true
  aspect-enabled: true
  include-stacktrace: true
  additional-sensitive-fields:
    - internalCustomerReference
    - legacyCredential
```

| Property | Default | Purpose |
|---|---:|---|
| `enabled` | `true` | Enables or disables the library itself. |
| `application-name` | `spring.application.name` | Overrides the name recorded in events. |
| `web-handler-enabled` | `true` | Enables uniform HTTP error responses. |
| `aspect-enabled` | `true` | Enables `@LogFailure` interception. |
| `include-stacktrace` | `true` | Adds a sanitized stack trace to the structured event. |
| `additional-sensitive-fields` | empty | Extends mandatory masking with domain-specific field names. |

Masking has no disable switch. Built-in sensitive rules cannot be replaced or removed.

## 4. Database operations

Annotate the service method where the application knows the table, operation, and relevant object:

```java
@LogFailure(
        table = "orders",
        operation = "INSERT",
        captureArgument = 0
)
public Order save(Order order) {
    return orderRepository.save(order);
}
```

`captureArgument` is zero-based. `0` selects the first method argument, `1` the second, and `-1` selects no argument. If a Spring `DataAccessException` or `SQLException` occurs, the event category is `DATABASE`.

The table is explicit because parsing SQL or driver messages would not be reliable across databases and versions.

Spring AOP only intercepts calls that pass through a Spring-managed proxy. Self-invocation inside the same bean does not trigger the annotation; use another bean or `ExceptionReporter` in that case.

## 5. Business exceptions

```java
throw new BusinessException(
        "ORDER_ALREADY_PAID",
        "The order has already been paid"
);
```

The default HTTP status is `422 Unprocessable Entity`. A custom status can be supplied:

```java
throw new BusinessException(
        "ORDER_NOT_FOUND",
        "The order was not found",
        HttpStatus.NOT_FOUND
);
```

Business codes should remain stable even if the human-readable message changes or is translated.

## 6. Connectivity failures

The default classifier recognizes common Spring client, connection, socket, and timeout exceptions as `CONNECTIVITY`.

```java
@LogFailure(operation = "GET_CUSTOMER", captureArgument = 0)
public Customer findCustomer(UUID customerId) {
    return customerClient.getCustomer(customerId);
}
```

When handled by the built-in HTTP advice, database and connectivity failures return `503 Service Unavailable` with the public code `DEPENDENCY_UNAVAILABLE`. Technical details stay in the log.

## 7. Programmatic reporting

Use the programmatic API for jobs, consumers, listeners, or dynamic context:

```java
@Service
public class PaymentProcessor {
    private final ExceptionReporter exceptionReporter;

    public PaymentProcessor(ExceptionReporter exceptionReporter) {
        this.exceptionReporter = exceptionReporter;
    }

    public void process(PaymentCommand command) {
        try {
            paymentClient.charge(command);
        } catch (RuntimeException error) {
            exceptionReporter.report(error, FailureContext.builder()
                    .operation("CHARGE_PAYMENT")
                    .failedObject(command)
                    .metadata("provider", "payments")
                    .metadata("paymentId", command.paymentId())
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
- Payment cards, CVV/CVC, PINs, IBANs, and bank accounts.
- Tax, identity, and passport identifiers.
- Names, e-mail addresses, phone numbers, addresses, and birth dates.

It also detects common sensitive patterns inside arbitrary text, including e-mails, IBANs, payment cards, JWTs, Bearer credentials, and assignments such as `password=...`.

Add domain-specific names without weakening defaults:

```yaml
exception-logging:
  additional-sensitive-fields:
    - customerAlias
    - policyHolderReference
    - legacyCredential
```

Automatic masking reduces risk but cannot infer every possible business meaning. Security and privacy review remains required before selecting production objects.

## 10. Correlation

`correlationId` and `traceId` are read from MDC. Ensure that an HTTP filter, message interceptor, or tracing platform adds them and clears them correctly after each request or message.

## 11. HTTP handling

| Failure | Status | Public code |
|---|---:|---|
| `BusinessException` | Configured status; 422 by default | Supplied business code |
| Database or connectivity | 503 | `DEPENDENCY_UNAVAILABLE` |
| Unexpected | 500 | `INTERNAL_ERROR` |

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

A service may also provide its own `ExceptionReporter` bean if it needs a corporate event transport or additional fields.

## 13. Troubleshooting

- `unknown-service`: configure `spring.application.name`.
- `table` is null: supply it through `@LogFailure` or `FailureContext`.
- `metadata.failedObject` is missing: select a valid `captureArgument` or call `.failedObject(...)`.
- Annotation does not run: check that AOP is enabled, the class is a Spring bean, and the call is not self-invocation.
- Advice conflict: set `web-handler-enabled=false`.
- A domain-sensitive value is visible: add its field name to `additional-sensitive-fields` and review similar domain fields.

## 14. Integration checklist

- [ ] The dependency comes from the corporate Maven repository.
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
