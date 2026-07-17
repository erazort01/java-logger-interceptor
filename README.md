# Exception Logging Spring Boot Starter

[English version](README.en.md)

Librería Java para aplicar el mismo tratamiento de excepciones y el mismo formato de logs en muchos microservicios Spring Boot. Clasifica errores de base de datos, negocio, conectividad e inesperados; añade el nombre del microservicio, correlación, tabla, operación y causa raíz; e incorpora en `metadata` el objeto completo relacionado con el fallo.

El objeto, los metadatos, los mensajes y la traza pasan siempre por un enmascarado obligatorio. Las reglas internas no pueden desactivarse ni reemplazarse mediante configuración; cada servicio únicamente puede añadir nombres de campos sensibles adicionales.

## Qué incluye

- Autoconfiguración al añadir la dependencia al microservicio.
- Generación, reutilización y propagación de un ID de traza entre microservicios.
- Clasificación extensible mediante `ExceptionClassifier`.
- Log JSON bajo el logger `exception.audit`, con stack trace saneado y configurable.
- `BusinessException` con código funcional y estado HTTP.
- Respuesta HTTP uniforme sin devolver detalles técnicos internos.
- Anotación `@LogFailure` para declarar tabla, operación y argumento que se incorporará completo a `metadata`.
- API `ExceptionReporter` para procesos asíncronos, consumidores, jobs y casos no HTTP.
- Deduplicación de una misma instancia de excepción.

## Estado actual

MVP funcional. Incluye el starter, la autoconfiguración y pruebas unitarias. Antes de desplegarlo en los 120 microservicios hay que sustituir el `groupId` de ejemplo, publicarlo en el repositorio Maven corporativo y validarlo en un pequeño grupo piloto.

## Requisitos

- Java 17 o superior.
- Spring Boot 3.x.
- Maven 3.9 o superior para compilar la librería.

## Instalación

Publicar primero el artefacto en Nexus, Artifactory o el repositorio Maven interno:

```bash
mvn clean deploy
```

Después, añadirlo a cada microservicio:

```xml
<dependency>
    <groupId>com.example.platform</groupId>
    <artifactId>exception-logging-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

El nombre del servicio se toma automáticamente de `spring.application.name`.

## Uso recomendado

Declarar el contexto donde se conoce la tabla y el objeto que se está procesando:

```java
@LogFailure(table = "orders", operation = "INSERT", captureArgument = 0)
public Order save(Order order) {
    return repository.save(order);
}
```

Para un consumidor, job o integración sin AOP:

```java
try {
    paymentClient.charge(command);
} catch (RuntimeException error) {
    exceptionReporter.report(error, FailureContext.builder()
            .operation("CHARGE_PAYMENT")
            .failedObject(command)
            .metadata("provider", "payments")
            .build());
    throw error;
}
```

Para reglas de negocio:

```java
throw new BusinessException("ORDER_ALREADY_PAID", "El pedido ya está pagado");
```

## Configuración

Configuración segura por defecto:

```yaml
spring:
  application:
    name: orders-service

exception-logging:
  enabled: true
  web-handler-enabled: true
  aspect-enabled: true
  trace-propagation-enabled: true
  trace-header-name: X-Trace-Id
  correlation-header-name: X-Correlation-Id
  include-stacktrace: true
  additional-sensitive-fields:
    - internalCustomerReference
    - legacyCredential
```

La lista obligatoria interna cubre credenciales, tokens, claves, datos de pago, identificadores fiscales y personales, nombres, contacto, dirección y fechas de nacimiento. `additional-sensitive-fields` solo amplía esa protección. No existe una propiedad para desactivar el enmascarado ni para eliminar reglas obligatorias.

## Formato del log

Ejemplo abreviado:

```json
{
  "microservice": "orders-service",
  "category": "DATABASE",
  "exceptionType": "org.springframework.dao.DataIntegrityViolationException",
  "message": "duplicate key",
  "rootCause": "duplicate key value violates unique constraint",
  "table": "orders",
  "operation": "INSERT",
  "correlationId": "req-7f8a",
  "traceId": "a4c31d",
  "metadata": {
    "method": "OrderService.save(..)",
    "failedObjectType": "com.example.Order",
    "failedObject": {
      "id": 42,
      "customerName": "[REDACTED]",
      "token": "[REDACTED]"
    }
  },
  "stackTrace": "java.lang.IllegalStateException: ..."
}
```

La librería genera automáticamente un ID cuando la petición no lo trae, reutiliza un `X-Trace-Id` válido, lo almacena en MDC, lo devuelve en la respuesta y lo propaga en clientes `RestClient` y `RestTemplate` construidos mediante Spring Boot. `X-Correlation-Id` se admite como cabecera heredada y se mantiene sincronizada con el ID de traza.

Para jobs, consumidores o tareas asíncronas:

```java
try (TraceScope scope = traceContext.open()) {
    executor.execute(traceContext.wrap(() -> process(command)));
}
```

El ámbito restaura el MDC anterior al cerrarse, evitando reutilizar accidentalmente una traza en otro trabajo del mismo hilo.

## Extensión por microservicio

Un servicio puede sustituir el clasificador sin modificar la librería:

```java
@Bean
ExceptionClassifier domainExceptionClassifier() {
    return error -> error instanceof MyLegacyException
            ? ErrorCategory.BUSINESS
            : new DefaultExceptionClassifier().classify(error);
}
```

También puede desactivar el manejador HTTP si ya dispone de uno propio y conservar solo el reporter y el aspecto:

```yaml
exception-logging:
  web-handler-enabled: false
```

## Adopción en 120 microservicios

1. Publicar una versión inmutable en el repositorio Maven interno.
2. Probarla en 3–5 servicios representativos: JDBC/JPA, integraciones HTTP y mensajería.
3. Validar el esquema JSON con la plataforma de logs y crear dashboards por `category`, `microservice` y `errorCode`.
4. Acordar la política corporativa de campos sensibles y retención.
5. Incorporarla mediante el BOM o parent POM corporativo.
6. Desplegar por oleadas y medir duplicados, volumen de logs y tiempo de respuesta.

## Verificación

```bash
mvn clean verify
```

## Estructura

```text
src/main/java/       API pública, clasificación, reporter y autoconfiguración
src/main/resources/  registro del starter de Spring Boot
src/test/java/       pruebas de clasificación, saneado y autoconfiguración
```

Consulta [GUIA_DE_USO.md](GUIA_DE_USO.md) para la integración detallada en español, [USAGE_GUIDE.md](USAGE_GUIDE.md) para la guía en inglés, [ARQUITECTURA.md](ARQUITECTURA.md) para las decisiones técnicas y [AGENTS.md](AGENTS.md) para las reglas de mantenimiento.
