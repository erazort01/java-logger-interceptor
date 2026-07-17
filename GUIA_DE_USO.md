# Guía de uso

Esta guía explica cómo integrar `exception-logging-spring-boot-starter` en un microservicio Spring Boot y cómo utilizarlo en operaciones de base de datos, reglas de negocio, llamadas remotas, consumidores y procesos programados.

## 1. Requisitos

- Java 17 o superior.
- Spring Boot 3.x.
- El artefacto publicado en el repositorio Maven corporativo.
- Un valor único en `spring.application.name` para cada microservicio.

## 2. Añadir la dependencia

Después de publicar la librería en Nexus o Artifactory, añadirla al `pom.xml` del microservicio:

```xml
<dependency>
    <groupId>com.example.platform</groupId>
    <artifactId>exception-logging-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

La librería se configura automáticamente. No es necesario añadir `@ComponentScan` ni importar manualmente su configuración.

Antes de distribuirla en la organización se deben sustituir el `groupId`, la versión y el repositorio de ejemplo por los valores corporativos.

## 3. Configuración mínima

Definir el nombre del microservicio:

```yaml
spring:
  application:
    name: orders-service
```

Este valor aparecerá en `microservice` dentro de todos los eventos. Si no existe, se utilizará `unknown-service`, lo que dificultará identificar el origen del fallo.

La configuración completa recomendada es:

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
    - passwd
    - secret
    - token
    - authorization
    - apiKey
    - creditCard
    - cvv
    - iban
    - ssn
```

### Propiedades disponibles

| Propiedad | Valor inicial | Función |
|---|---:|---|
| `enabled` | `true` | Activa o desactiva toda la librería. |
| `application-name` | `spring.application.name` | Permite sobrescribir el nombre mostrado en los logs. |
| `web-handler-enabled` | `true` | Activa las respuestas HTTP uniformes. |
| `aspect-enabled` | `true` | Activa el procesamiento de `@LogFailure`. |
| `capture-object` | `false` | Permite incorporar al log el argumento indicado en la anotación o el objeto del contexto. |
| `include-stacktrace` | `true` | Incluye la traza completa en el backend de logging. |
| `max-object-length` | `4000` | Limita el JSON del objeto; si se supera, se trunca. |
| `sensitive-fields` | lista segura inicial | Nombres de campos que se sustituyen por `[REDACTED]`. |

## 4. Uso en operaciones de base de datos

Anotar el método de servicio donde se conoce la tabla, la operación y el objeto procesado:

```java
import com.example.platform.exceptionlogging.LogFailure;

@LogFailure(
        table = "orders",
        operation = "INSERT",
        captureArgument = 0
)
public Order save(Order order) {
    return orderRepository.save(order);
}
```

Si `orderRepository.save` provoca una excepción Spring `DataAccessException` o una `SQLException`, el evento tendrá la categoría `DATABASE`.

`captureArgument = 0` hace referencia al primer argumento del método. El índice empieza en cero:

- `0`: primer argumento.
- `1`: segundo argumento.
- `-1`: no seleccionar ningún argumento.

El nombre de la tabla es explícito. La librería no intenta deducirlo analizando SQL o el mensaje del driver porque ese método no es fiable entre bases de datos, idiomas y versiones.

### Actualización y borrado

```java
@LogFailure(table = "customers", operation = "UPDATE", captureArgument = 0)
public Customer update(Customer customer) {
    return repository.save(customer);
}

@LogFailure(table = "customers", operation = "DELETE", captureArgument = 0)
public void delete(UUID customerId) {
    repository.deleteById(customerId);
}
```

### Consideraciones de AOP

Para que la anotación funcione, el método debe ejecutarse a través de un bean administrado por Spring. Una llamada de un método a otro dentro de la misma instancia —autoinvocación— no atraviesa el proxy y no activa el aspecto. En ese caso se debe mover la operación a otro servicio o usar `ExceptionReporter` directamente.

## 5. Excepciones de negocio

Lanzar `BusinessException` con un código estable y un mensaje comprensible:

```java
import com.example.platform.exceptionlogging.BusinessException;

if (order.isPaid()) {
    throw new BusinessException(
            "ORDER_ALREADY_PAID",
            "El pedido ya está pagado"
    );
}
```

Por defecto, una excepción de negocio devuelve HTTP `422 Unprocessable Entity`.

Se puede indicar otro estado:

```java
throw new BusinessException(
        "ORDER_NOT_FOUND",
        "No se ha encontrado el pedido",
        HttpStatus.NOT_FOUND
);
```

Respuesta HTTP aproximada:

```json
{
  "timestamp": "2026-07-17T18:30:00Z",
  "status": 404,
  "code": "ORDER_NOT_FOUND",
  "message": "No se ha encontrado el pedido",
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
@LogFailure(operation = "GET_CUSTOMER", captureArgument = 0)
public Customer findCustomer(UUID customerId) {
    return customerClient.getCustomer(customerId);
}
```

Si la excepción llega al manejador HTTP, la respuesta será `503 Service Unavailable` con el código público `DEPENDENCY_UNAVAILABLE`. El detalle técnico permanece en el log, no en la respuesta al cliente.

## 7. Uso programático

La anotación es cómoda para métodos Spring. Para jobs, consumidores, listeners o flujos donde se necesita contexto dinámico, inyectar `ExceptionReporter`:

```java
import com.example.platform.exceptionlogging.ExceptionReporter;
import com.example.platform.exceptionlogging.FailureContext;

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

Es importante relanzar la excepción cuando el flujo original deba seguir considerándose fallido. El reporter registra el error, pero no decide reintentos, transacciones ni confirmaciones de mensajes.

Para registrar una excepción sin contexto adicional:

```java
exceptionReporter.report(error);
```

## 8. Captura segura del objeto

Para adjuntar el objeto relacionado hay que cumplir dos condiciones:

1. Activar globalmente `exception-logging.capture-object=true`.
2. Seleccionar un argumento con `captureArgument` o proporcionar `failedObject` mediante `FailureContext`.

```yaml
exception-logging:
  capture-object: true
  max-object-length: 4000
  sensitive-fields:
    - password
    - accessToken
    - refreshToken
    - authorization
    - iban
```

Ejemplo de resultado:

```json
{
  "id": "order-123",
  "customer": "customer-42",
  "accessToken": "[REDACTED]"
}
```

La ocultación se aplica de forma recursiva y sin distinguir mayúsculas. Si el JSON supera el límite, se convierte en texto truncado terminado en `[TRUNCATED]`.

Recomendaciones:

- Preferir comandos o DTO pequeños frente a entidades completas.
- No registrar contraseñas, credenciales, documentos, información sanitaria ni datos de tarjeta.
- Añadir a `sensitive-fields` todos los nombres utilizados por cada dominio.
- No suponer que el saneado por nombre de campo cubre cualquier dato personal.
- Desactivar la captura en servicios donde no sea necesaria.

## 9. Correlación y trazabilidad

La librería lee `correlationId` y `traceId` desde MDC. Si la plataforma de observabilidad ya los incorpora, no se necesita configuración adicional.

Un filtro sencillo para `correlationId` podría ser:

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String correlationId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            response.setHeader(HEADER, correlationId);
            chain.doFilter(request, response);
        }
    }
}
```

El identificador recibido debe validarse y limitarse de longitud antes de incorporarlo al MDC en una implementación de producción.

## 10. Formato del evento

El logger dedicado es `exception.audit`. Un evento puede tener este aspecto:

```json
{
  "timestamp": "2026-07-17T18:30:00Z",
  "microservice": "orders-service",
  "category": "DATABASE",
  "errorCode": null,
  "exceptionType": "org.springframework.dao.DataIntegrityViolationException",
  "message": "duplicate key",
  "rootCause": "duplicate key value violates unique constraint",
  "table": "orders",
  "operation": "INSERT",
  "correlationId": "req-7f8a",
  "traceId": "a4c31d",
  "objectType": "com.example.orders.OrderCommand",
  "objectSnapshot": {
    "id": "order-123",
    "token": "[REDACTED]"
  },
  "metadata": {
    "method": "OrderService.save(..)"
  }
}
```

Categorías posibles:

| Categoría | Uso |
|---|---|
| `DATABASE` | Errores JDBC, SQL y excepciones Spring de acceso a datos. |
| `BUSINESS` | Reglas de negocio representadas por `BusinessException`. |
| `CONNECTIVITY` | Fallos de red, clientes remotos y timeouts. |
| `UNEXPECTED` | Cualquier excepción que no encaje en las categorías anteriores. |

## 11. Manejador HTTP

Con `web-handler-enabled=true`, la librería aplica estas respuestas:

| Tipo | Estado | Código público |
|---|---:|---|
| `BusinessException` | El indicado en la excepción; por defecto 422 | Código de negocio proporcionado. |
| Base de datos o conectividad | 503 | `DEPENDENCY_UNAVAILABLE` |
| Inesperada | 500 | `INTERNAL_ERROR` |

Las respuestas 500 y 503 no incluyen el mensaje técnico de la excepción.

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

También se puede sustituir completamente `ExceptionReporter` creando un bean propio, por ejemplo para emitir eventos a una cola o añadir campos corporativos.

## 13. Evitar logs duplicados

La librería identifica la misma instancia de excepción y solo la registra una vez. Esto permite usar `@LogFailure` en la capa de servicio y dejar que el manejador HTTP procese posteriormente la misma excepción.

Si una aplicación captura una excepción y crea otra instancia sin conservar la causa, la nueva excepción se considera distinta. Lo correcto es conservar la cadena causal:

```java
throw new OrderPersistenceException("No se pudo guardar el pedido", originalError);
```

## 14. Problemas frecuentes

### El microservicio aparece como `unknown-service`

Falta `spring.application.name` o no se ha configurado `exception-logging.application-name`.

### La tabla aparece como `null`

La tabla solo se conoce cuando se proporciona en `@LogFailure(table = "...")` o mediante `FailureContext.builder().table("...")`.

### El objeto aparece como `null`

Comprobar que `capture-object` está activado y que `captureArgument` apunta a un índice válido, o que se ha indicado `failedObject`.

### La anotación no registra nada

Comprobar que el aspecto está activado, que la clase es un bean Spring y que la llamada no es una autoinvocación dentro del mismo objeto.

### Hay conflicto con el manejador de excepciones del servicio

Configurar `exception-logging.web-handler-enabled=false` y conservar el manejador propio.

### Un dato sensible no se oculta

Añadir el nombre exacto del campo a `sensitive-fields`. La comparación ignora mayúsculas, pero no reconoce automáticamente sinónimos ni el significado del contenido.

## 15. Lista de comprobación de integración

- [ ] La dependencia procede del repositorio Maven corporativo.
- [ ] `spring.application.name` identifica de forma única el servicio.
- [ ] Se ha decidido si se utilizará el manejador HTTP de la librería o el existente.
- [ ] Las operaciones críticas llevan tabla y operación explícitas.
- [ ] La captura de objetos permanece desactivada o ha pasado revisión de seguridad.
- [ ] La lista de campos sensibles está adaptada al dominio.
- [ ] `correlationId` y `traceId` llegan al MDC.
- [ ] Los eventos de `exception.audit` aparecen correctamente en la plataforma de logs.
- [ ] Se han probado errores de negocio, base de datos, timeout e inesperados.
- [ ] Se han revisado volumen, retención y alertas antes de producción.

## 16. Prueba rápida

Crear temporalmente un método de prueba:

```java
@LogFailure(table = "orders", operation = "TEST", captureArgument = 0)
public void simulateFailure(Map<String, Object> input) {
    throw new IllegalStateException("Fallo controlado de prueba");
}
```

Invocarlo con:

```java
Map.of("orderId", "order-123", "token", "secret-value")
```

Verificar que:

- `microservice` coincide con el servicio.
- La categoría es `UNEXPECTED`.
- La tabla es `orders`.
- El token aparece como `[REDACTED]` si la captura está activada.
- La API devuelve un mensaje genérico y no el detalle técnico.

Eliminar la prueba controlada después de validar la integración.

