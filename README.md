# Java Logger Interceptor

[English version](README.en.md)

Librería Java genérica para aplicar un tratamiento uniforme de excepciones, logs estructurados y propagación de trazas en cualquier microservicio Spring Boot. Clasifica errores de autenticación (`AUTH`), base de datos, negocio, conectividad e inesperados; añade el nombre del microservicio, correlación, tabla, operación y causa raíz; e incorpora en `metadata` el objeto completo relacionado con el fallo.

El objeto, los metadatos, los mensajes y la traza pasan siempre por un enmascarado obligatorio. Las reglas internas no pueden desactivarse ni reemplazarse mediante configuración; cada servicio únicamente puede añadir nombres de campos sensibles adicionales.

## Qué incluye

- Autoconfiguración al añadir la dependencia al microservicio.
- Generación, reutilización y propagación de un ID de traza entre microservicios.
- Clasificación extensible mediante `ExceptionClassifier`.
- Categoría `AUTH` para excepciones de autenticación de Spring Security, JAAS y respuestas HTTP 401, sin confundirlas con errores de autorización 403.
- Log JSON bajo el logger `exception.audit`, con stack trace saneado y configurable.
- `BusinessException` con código funcional y estado HTTP.
- Respuesta HTTP uniforme sin devolver detalles técnicos internos.
- Anotación `@LogFailure` para declarar tabla, operación y argumento que se incorporará completo a `metadata`.
- API `ExceptionReporter` para procesos asíncronos, consumidores, jobs y casos no HTTP.
- Deduplicación de una misma instancia de excepción.

## Estado actual

MVP funcional. Incluye el starter, la autoconfiguración y pruebas unitarias. Antes de utilizarlo en producción hay que sustituir el `groupId` de ejemplo, publicarlo en el repositorio Maven correspondiente y validarlo en servicios representativos.

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
    <artifactId>java-logger-interceptor</artifactId>
    <version>0.1.0</version>
</dependency>
```

El nombre del servicio se toma automáticamente de `spring.application.name`.

## Uso recomendado

Declarar el contexto donde se conoce la tabla y el objeto que se está procesando:

```java
@LogFailure(table = "example_records", operation = "INSERT", captureArgument = 0)
public ExampleRecord save(ExampleRecord record) {
    return repository.save(record);
}
```

Para un consumidor, job o integración sin AOP:

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

Para reglas de negocio:

```java
throw new BusinessException("RESOURCE_STATE_INVALID", "El recurso no se encuentra en un estado válido");
```

## Configuración

Configuración segura por defecto:

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

La lista obligatoria interna cubre credenciales, tokens, claves, identificadores personales, nombres, contacto, dirección y fechas de nacimiento. `additional-sensitive-fields` solo amplía esa protección. No existe una propiedad para desactivar el enmascarado ni para eliminar reglas obligatorias.

## Formato del log

Ejemplo abreviado:

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

## Adopción en microservicios Spring Boot

1. Publicar una versión inmutable en el repositorio Maven interno.
2. Probarla en 3–5 servicios representativos: JDBC/JPA, integraciones HTTP y mensajería.
3. Validar el esquema JSON con la plataforma de logs y crear dashboards por `category`, `microservice` y `errorCode`.
4. Acordar la política de campos sensibles y retención del entorno de destino.
5. Incorporarla mediante un BOM o parent POM cuando resulte conveniente.
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
