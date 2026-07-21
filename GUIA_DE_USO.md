# Guía de uso

[English usage guide](USAGE_GUIDE.md)

Esta guía explica cómo integrar `java-logger-interceptor` en cualquier microservicio Spring Boot y cómo utilizarlo en operaciones de base de datos, reglas de negocio, llamadas remotas, consumidores y procesos programados.

## 1. Requisitos

- Java 17 o superior.
- Spring Boot 3.x.
- El artefacto disponible en un repositorio Maven accesible para el microservicio.
- Un valor único en `spring.application.name` para cada microservicio.

## 2. Añadir la dependencia

Después de publicar la librería en Nexus o Artifactory, añadirla al `pom.xml` del microservicio:

```xml
<dependency>
    <groupId>com.example.platform</groupId>
    <artifactId>java-logger-interceptor</artifactId>
    <version>0.1.0</version>
</dependency>
```

La librería se configura automáticamente. No es necesario añadir `@ComponentScan` ni importar manualmente su configuración.

Antes de distribuirla se deben sustituir el `groupId`, la versión y el repositorio de ejemplo por los valores del entorno de destino.

## 3. Configuración mínima

Definir el nombre del microservicio:

```yaml
spring:
  application:
    name: example-service
```

Este valor aparecerá en `microservice` dentro de todos los eventos. Si no existe, se utilizará `unknown-service`, lo que dificultará identificar el origen del fallo.

La configuración completa recomendada es:

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

### Propiedades disponibles

| Propiedad | Valor inicial | Función |
|---|---:|---|
| `enabled` | `true` | Activa o desactiva toda la librería. |
| `application-name` | `spring.application.name` | Permite sobrescribir el nombre mostrado en los logs. |
| `web-handler-enabled` | `true` | Activa las respuestas HTTP uniformes. |
| `aspect-enabled` | `true` | Activa el procesamiento de `@LogFailure`. |
| `trace-propagation-enabled` | `true` | Genera y propaga el contexto de traza HTTP. |
| `trace-header-name` | `X-Trace-Id` | Cabecera canónica del ID de traza. |
| `correlation-header-name` | `X-Correlation-Id` | Cabecera heredada compatible. |
| `include-stacktrace` | `true` | Incluye la traza completa en el backend de logging. |
| `additional-sensitive-fields` | lista vacía | Añade nombres específicos del dominio a las reglas obligatorias; no permite eliminar las internas. |

No existe ninguna propiedad que desactive el enmascarado. El objeto seleccionado, los metadatos, los mensajes de excepción y el stack trace se sanean siempre.

## 4. Uso en operaciones de base de datos

Anotar el método de servicio donde se conoce la tabla, la operación y el objeto procesado:

```java
import com.example.platform.exceptionlogging.LogFailure;

@LogFailure(
        table = "example_records",
        operation = "INSERT",
        captureArgument = 0
)
public ExampleRecord save(ExampleRecord record) {
    return repository.save(record);
}
```

Si `repository.save` provoca una excepción Spring `DataAccessException` o una `SQLException`, el evento tendrá la categoría `DATABASE`.

`captureArgument = 0` hace referencia al primer argumento del método. El índice empieza en cero:

- `0`: primer argumento.
- `1`: segundo argumento.
- `-1`: no seleccionar ningún argumento.

El nombre de la tabla es explícito. La librería no intenta deducirlo analizando SQL o el mensaje del driver porque ese método no es fiable entre bases de datos, idiomas y versiones.

### Actualización y borrado

```java
@LogFailure(table = "example_records", operation = "UPDATE", captureArgument = 0)
public ExampleRecord update(ExampleRecord record) {
    return repository.save(record);
}

@LogFailure(table = "example_records", operation = "DELETE", captureArgument = 0)
public void delete(UUID recordId) {
    repository.deleteById(recordId);
}
```

### Consideraciones de AOP

Para que la anotación funcione, el método debe ejecutarse a través de un bean administrado por Spring. Una llamada de un método a otro dentro de la misma instancia —autoinvocación— no atraviesa el proxy y no activa el aspecto. En ese caso se debe mover la operación a otro servicio o usar `ExceptionReporter` directamente.

## 5. Excepciones de negocio

Lanzar `BusinessException` con un código estable y un mensaje comprensible:

```java
import com.example.platform.exceptionlogging.BusinessException;

if (!resource.canTransition()) {
    throw new BusinessException(
            "RESOURCE_STATE_INVALID",
            "El recurso no se encuentra en un estado válido"
    );
}
```

Por defecto, una excepción de negocio devuelve HTTP `422 Unprocessable Entity`.

Se puede indicar otro estado:

```java
throw new BusinessException(
        "RESOURCE_NOT_FOUND",
        "No se ha encontrado el recurso",
        HttpStatus.NOT_FOUND
);
```

Respuesta HTTP aproximada:

```json
{
  "timestamp": "2026-07-17T18:30:00Z",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "message": "No se ha encontrado el recurso",
  "correlationId": "req-7f8a"
}
```

Los códigos de negocio deben ser estables. No conviene utilizar el texto del mensaje como código, ya que el mensaje puede traducirse o cambiar.

## 6. Excepciones de conectividad

Las excepciones de clientes Spring y las causas de red más comunes se clasifican como `CONNECTIVITY`:

- `RestClientException`.
- `ConnectException`.
- `SocketException`.
- `SocketTimeoutException`.
- `TimeoutException`.

Ejemplo con contexto explícito:

```java
@LogFailure(operation = "GET_REMOTE_RESOURCE", captureArgument = 0)
public RemoteResource findRemoteResource(UUID resourceId) {
    return remoteClient.getResource(resourceId);
}
```

Si la excepción llega al manejador HTTP, la respuesta será `503 Service Unavailable` con el código público `DEPENDENCY_UNAVAILABLE`. El detalle técnico permanece en el log, no en la respuesta al cliente.

## 7. Uso programático

La anotación es cómoda para métodos Spring. Para jobs, consumidores, listeners o flujos donde se necesita contexto dinámico, inyectar `ExceptionReporter`:

```java
import com.example.platform.exceptionlogging.ExceptionReporter;
import com.example.platform.exceptionlogging.FailureContext;

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

Es importante relanzar la excepción cuando el flujo original deba seguir considerándose fallido. El reporter registra el error, pero no decide reintentos, transacciones ni confirmaciones de mensajes.

Para registrar una excepción sin contexto adicional:

```java
exceptionReporter.report(error);
```

## 8. Incorporación segura del objeto en metadata

Para adjuntar el objeto relacionado hay que seleccionarlo con `captureArgument` o proporcionar `failedObject` mediante `FailureContext`. No es necesaria ni existe una opción global de activación.

```yaml
exception-logging:
  additional-sensitive-fields:
    - internalAlias
    - legacyCredential
```

Ejemplo de resultado:

```json
{
  "id": "record-123",
  "owner": "user-42",
  "accessToken": "[REDACTED]"
}
```

La ocultación se aplica de forma recursiva y sin distinguir mayúsculas. Las reglas obligatorias cubren credenciales, tokens, claves, identificadores personales, nombres, datos de contacto, direcciones y fechas de nacimiento. También se enmascaran patrones sensibles en texto libre, como correos, JWT y cabeceras Bearer.

Recomendaciones:

- Preferir comandos o DTO pequeños frente a entidades completas.
- No registrar contraseñas, credenciales, documentos ni información personal que no sea necesaria para diagnosticar el fallo.
- Añadir a `additional-sensitive-fields` cualquier nombre propio del dominio que pueda contener información sensible.
- No suponer que un sistema automático puede reconocer cualquier dato sensible con independencia del nombre y del formato.
- No seleccionar un objeto si no aporta valor de diagnóstico; cuando se selecciona, se incorpora completo en `metadata`.

## 9. Correlación y trazabilidad

La librería gestiona el ID automáticamente en peticiones HTTP:

- Si llega un `X-Trace-Id` válido, lo reutiliza.
- Si no llega, acepta `X-Correlation-Id` como compatibilidad heredada.
- Si ninguno es válido, genera un UUID nuevo.
- Guarda el mismo valor en `traceId` y `correlationId` dentro de MDC.
- Devuelve ambos headers en la respuesta.
- Restaura el MDC anterior cuando termina la petición.

Los valores externos deben tener entre 8 y 128 caracteres y solo pueden contener letras, números, punto, guion y guion bajo. Cualquier valor con saltos de línea o caracteres no permitidos se descarta y se sustituye por uno nuevo.

### Llamadas entre microservicios

Los clientes `RestClient` y `RestTemplate` creados con los builders autoconfigurados de Spring Boot reciben un interceptor que añade el mismo ID:

```java
@Service
public class RemoteServiceGateway {
    private final RestClient restClient;

    public RemoteServiceGateway(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://remote-service").build();
    }
}
```

No se debe crear el cliente con `new RestTemplate()` porque esa instancia no recibe los customizers de Spring Boot. Para Feign, WebClient u otro cliente, usar `TraceContext.currentTraceId()` en su interceptor propio o registrar `TracePropagationInterceptor` cuando el cliente admita `ClientHttpRequestInterceptor`.

### Jobs y consumidores

Abrir un ámbito al comenzar cada unidad de trabajo:

```java
try (TraceScope scope = traceContext.open(message.getTraceId())) {
    process(message);
}
```

Si el mensaje no contiene un ID:

```java
try (TraceScope scope = traceContext.open()) {
    process(message);
}
```

`scope.traceId()` devuelve el valor que debe enviarse en los headers del siguiente mensaje.

### Tareas asíncronas

MDC es local al hilo. Para transportar el ID a un `Executor`, envolver la tarea mientras el contexto de origen sigue abierto:

```java
executor.execute(traceContext.wrap(() -> process(command)));
```

`wrap` captura el ID actual, abre el mismo contexto en el hilo de destino y restaura su MDC al terminar.

## 10. Formato del evento

El logger dedicado es `exception.audit`. Un evento puede tener este aspecto:

```json
{
  "timestamp": "2026-07-17T18:30:00Z",
  "microservice": "example-service",
  "category": "DATABASE",
  "errorCode": null,
  "exceptionType": "org.springframework.dao.DataIntegrityViolationException",
  "message": "duplicate key",
  "rootCause": "duplicate key value violates unique constraint",
  "table": "example_records",
  "operation": "INSERT",
  "correlationId": "req-7f8a",
  "traceId": "a4c31d",
  "metadata": {
    "method": "ExampleService.save(..)",
    "failedObjectType": "com.example.ExampleCommand",
    "failedObject": {
      "id": "record-123",
      "ownerName": "[REDACTED]",
      "token": "[REDACTED]"
    }
  },
  "stackTrace": "java.lang.IllegalStateException: ..."
}
```

Categorías posibles:

| Categoría | Uso |
|---|---|
| `AUTH` | Fallos de autenticación de Spring Security o JAAS y errores HTTP 401. No incluye fallos de autorización 403. |
| `DATABASE` | Errores JDBC, SQL y excepciones Spring de acceso a datos. |
| `BUSINESS` | Reglas de negocio representadas por `BusinessException`. |
| `CONNECTIVITY` | Fallos de red, clientes remotos y timeouts. |
| `UNEXPECTED` | Cualquier excepción que no encaje en las categorías anteriores. |

## 11. Manejador HTTP

Con `web-handler-enabled=true`, la librería aplica estas respuestas:

| Tipo | Estado | Código público |
|---|---:|---|
| Autenticación | 401 | `AUTHENTICATION_FAILED` |
| `BusinessException` | El indicado en la excepción; por defecto 422 | Código de negocio proporcionado. |
| Base de datos o conectividad | 503 | `DEPENDENCY_UNAVAILABLE` |
| Inesperada | 500 | `INTERNAL_ERROR` |

Las respuestas 401, 500 y 503 no incluyen el mensaje técnico de la excepción.

Si el microservicio ya tiene un `@RestControllerAdvice`, desactivar el de la librería para evitar decisiones ambiguas:

```yaml
exception-logging:
  web-handler-enabled: false
```

El microservicio puede conservar `@LogFailure` y `ExceptionReporter` aunque desactive el manejador HTTP.

## 12. Personalizar la clasificación

Registrar un bean propio de `ExceptionClassifier`. La autoconfiguración detectará el bean y no creará el clasificador predeterminado:

```java
@Configuration
public class ExceptionClassificationConfiguration {
    @Bean
    ExceptionClassifier exceptionClassifier() {
        DefaultExceptionClassifier defaults = new DefaultExceptionClassifier();

        return error -> {
            if (containsCause(error, LegacyBusinessException.class)) {
                return ErrorCategory.BUSINESS;
            }
            return defaults.classify(error);
        };
    }

    private static boolean containsCause(Throwable error, Class<?> expected) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (expected.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
```

También se puede sustituir completamente `ExceptionReporter` creando un bean propio, por ejemplo para emitir eventos a una cola o añadir campos personalizados.

## 13. Evitar logs duplicados

La librería identifica la misma instancia de excepción y solo la registra una vez. Esto permite usar `@LogFailure` en la capa de servicio y dejar que el manejador HTTP procese posteriormente la misma excepción.

Si una aplicación captura una excepción y crea otra instancia sin conservar la causa, la nueva excepción se considera distinta. Lo correcto es conservar la cadena causal:

```java
throw new ResourcePersistenceException("No se pudo guardar el recurso", originalError);
```

## 14. Problemas frecuentes

### El microservicio aparece como `unknown-service`

Falta `spring.application.name` o no se ha configurado `exception-logging.application-name`.

### La tabla aparece como `null`

La tabla solo se conoce cuando se proporciona en `@LogFailure(table = "...")` o mediante `FailureContext.builder().table("...")`.

### El objeto aparece como `null`

Comprobar que `captureArgument` apunta a un índice válido, o que se ha indicado `failedObject`.

### La anotación no registra nada

Comprobar que el aspecto está activado, que la clase es un bean Spring y que la llamada no es una autoinvocación dentro del mismo objeto.

### Hay conflicto con el manejador de excepciones del servicio

Configurar `exception-logging.web-handler-enabled=false` y conservar el manejador propio.

### Un dato sensible no se oculta

Añadir el nombre del campo a `additional-sensitive-fields`. La comparación ignora mayúsculas y separadores, pero ningún sistema automático puede reconocer todos los significados posibles de un campo arbitrario.

## 15. Lista de comprobación de integración

- [ ] La dependencia procede del repositorio Maven configurado para el servicio.
- [ ] `spring.application.name` identifica de forma única el servicio.
- [ ] Se ha decidido si se utilizará el manejador HTTP de la librería o el existente.
- [ ] Las operaciones críticas llevan tabla y operación explícitas.
- [ ] Los objetos seleccionados son necesarios para diagnosticar el fallo y han pasado revisión de seguridad.
- [ ] `additional-sensitive-fields` está adaptado al dominio sin intentar sustituir las reglas obligatorias.
- [ ] `correlationId` y `traceId` llegan al MDC.
- [ ] Los eventos de `exception.audit` aparecen correctamente en la plataforma de logs.
- [ ] Se han probado errores de negocio, base de datos, timeout e inesperados.
- [ ] Se han revisado volumen, retención y alertas antes de producción.

## 16. Prueba rápida

Crear temporalmente un método de prueba:

```java
@LogFailure(table = "example_records", operation = "TEST", captureArgument = 0)
public void simulateFailure(Map<String, Object> input) {
    throw new IllegalStateException("Fallo controlado de prueba");
}
```

Invocarlo con:

```java
Map.of("recordId", "record-123", "token", "secret-value")
```

Verificar que:

- `microservice` coincide con el servicio.
- La categoría es `UNEXPECTED`.
- La tabla es `example_records`.
- El objeto aparece dentro de `metadata.failedObject` y el token figura como `[REDACTED]`.
- La API devuelve un mensaje genérico y no el detalle técnico.

Eliminar la prueba controlada después de validar la integración.
