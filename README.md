# Exception Logging Spring Boot Starter

Librería Java para aplicar el mismo tratamiento de excepciones y el mismo formato de logs en muchos microservicios Spring Boot. Clasifica errores de base de datos, negocio, conectividad e inesperados; añade el nombre del microservicio, correlación, tabla, operación y causa raíz; y puede adjuntar una copia saneada del objeto relacionado con el fallo.

El objeto no se registra por defecto. Cuando se activa, los campos sensibles se ocultan y el contenido se limita de tamaño para reducir el riesgo de exponer contraseñas, tokens o datos personales.

## Qué incluye

- Autoconfiguración al añadir la dependencia al microservicio.
- Clasificación extensible mediante `ExceptionClassifier`.
- Log JSON bajo el logger `exception.audit`, con stack trace configurable.
- `BusinessException` con código funcional y estado HTTP.
- Respuesta HTTP uniforme sin devolver detalles técnicos internos.
- Anotación `@LogFailure` para declarar tabla, operación y argumento relacionado.
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
  capture-object: false
  include-stacktrace: true
  max-object-length: 4000
  sensitive-fields:
    - password
    - token
    - authorization
    - secret
    - iban
```

Activar `capture-object` solo en servicios y casos revisados. La lista de campos sensibles debe ampliarse según el modelo de datos de la organización. Nunca se deben adjuntar entidades completas que contengan información sanitaria, financiera, credenciales o datos personales no necesarios.

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
  "objectType": "com.example.Order",
  "objectSnapshot": {"id": 42, "token": "[REDACTED]"}
}
```

Los valores `correlationId` y `traceId` se leen de MDC. El gateway, filtro HTTP o plataforma de trazas debe poblarlos.

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

Consulta [ARQUITECTURA.md](ARQUITECTURA.md) para las decisiones técnicas y [AGENTS.md](AGENTS.md) para las reglas de mantenimiento.

