# ARQUITECTURA

## Objetivo del sistema

Proporcionar un componente transversal para que los microservicios Java clasifiquen, registren y traduzcan excepciones de forma uniforme sin acoplar la librería a una base de datos, ORM, dominio o proveedor de conectividad concretos.

## Problema que resuelve

Centraliza el formato de los logs y el tratamiento HTTP de errores de base de datos, negocio, conectividad e inesperados. Permite incorporar contexto conocido por la aplicación, como tabla, operación y objeto afectado.

No sustituye a una plataforma de observabilidad, a la instrumentación de trazas, a las políticas de reintentos ni a la gestión de alertas. Tampoco intenta extraer de forma automática y no fiable el nombre de una tabla a partir de SQL o mensajes del driver.

## Alcance actual

- Starter de Spring Boot con configuración automática.
- Clasificador predeterminado y punto de extensión para clasificadores personalizados.
- Reporter SLF4J con evento JSON, contexto, saneado y deduplicación.
- Aspecto optativo y manejador global HTTP optativo.
- Excepción de negocio base con código estable.

## Principios arquitectónicos

- La aplicación aporta el contexto que solo ella conoce; la librería normaliza y registra.
- Las interfaces públicas permiten sustituir clasificación y reporte con beans propios.
- No se exponen mensajes técnicos internos en respuestas HTTP inesperadas.
- El objeto seleccionado se incorpora completo en `metadata` y el saneado es obligatorio y no desactivable.
- La adopción no exige herencia ni cambios en los repositorios existentes.

## Componentes principales

- `ExceptionClassifier`: recorre la cadena causal y asigna una categoría.
- `ExceptionReporter`: contrato de registro para cualquier tipo de ejecución.
- `Slf4jExceptionReporter`: construye el evento, añade MDC, sanea el objeto y emite el log.
- `FailureContext`: contexto explícito de tabla, operación, objeto y metadatos.
- `LogFailureAspect`: captura fallos en métodos anotados y construye el contexto.
- `GlobalExceptionHandler`: traduce excepciones a un contrato HTTP estable.
- `ExceptionLoggingAutoConfiguration`: activa componentes y permite reemplazarlos.
- `TraceContext`: crea ámbitos reutilizables, conserva el ID en MDC y transporta el contexto a tareas asíncronas.
- `TracePropagationFilter`: acepta o genera el ID de entrada y lo devuelve al cliente.
- `TracePropagationInterceptor`: añade el ID a llamadas HTTP salientes.

## Flujo principal

1. El filtro reutiliza un ID entrante válido o genera uno nuevo y abre el ámbito MDC.
2. Los clientes HTTP salientes propagan ese mismo ID a los siguientes microservicios.
3. Una operación anotada o un manejador HTTP recibe una excepción.
4. El reporter evita registrar dos veces la misma instancia y clasifica su cadena causal.
5. Recoge nombre del microservicio, traza, causa raíz y contexto proporcionado.
6. Convierte los metadatos y el objeto completo a JSON y aplica reglas obligatorias de enmascarado.
7. Sanea también mensajes y stack trace antes de emitir el evento mediante `exception.audit`.
8. El manejador devuelve la respuesta y el filtro cierra el ámbito, restaurando el MDC previo.

## Impacto del consenso funcional

- Decisión funcional: lograr una integración homogénea en cualquier microservicio Spring Boot sin conocer su dominio concreto.
- Módulos afectados: un único starter autocontenido con API y autoconfiguración.
- Contrato principal: `ExceptionLogEvent`; sus campos deben evolucionar de forma compatible.
- Restricciones: Java 17, Spring Boot 3, bajo acoplamiento y protección de datos.
- Riesgos mitigados: filtrado obligatorio de secretos y patrones sensibles, respuesta HTTP sin causa interna y stack trace saneado.

## Estructura técnica del repositorio

```text
/
|-- AGENTS.md
|-- ARQUITECTURA.md
|-- README.md
|-- pom.xml
`-- src/
    |-- main/java/com/example/platform/exceptionlogging/
    |-- main/resources/META-INF/spring/
    `-- test/java/com/example/platform/exceptionlogging/
```

## Datos y contratos principales

- `ExceptionLogEvent`: esquema del evento de observabilidad.
- `FailureContext`: datos opcionales aportados por el microservicio.
- `ApiError`: respuesta HTTP sin información técnica sensible.
- `BusinessException`: código de negocio, mensaje y estado HTTP.

La librería no persiste datos.

## Integraciones externas

- SLF4J como fachada de logging; el microservicio decide el backend y transporte.
- MDC para `correlationId` y `traceId`, gestionado mediante ámbitos restaurables.
- Spring MVC cuando está disponible.
- `RestClient` y `RestTemplate` construidos por Spring Boot para propagación HTTP saliente.
- Excepciones Spring JDBC/DAO y cliente REST para la clasificación predeterminada.

## Despliegue y operación

- Runtime: Java 17 y Spring Boot 3.x.
- Distribución: artefacto Maven versionado en Nexus o Artifactory.
- Configuración: propiedades `exception-logging.*`; no usa secretos propios.
- Despliegue: junto con cada microservicio consumidor.
- Evolución: versiones semánticas y BOM o parent POM cuando el entorno consumidor lo requiera.

## Seguridad

- Lista interna obligatoria de campos sensibles, aplicada recursivamente sin distinguir mayúsculas.
- Detección adicional de correos, JWT, cabeceras Bearer y asignaciones de credenciales en texto libre.
- Los servicios solo pueden añadir reglas mediante `additional-sensitive-fields`; no pueden eliminar ni desactivar las obligatorias.
- El objeto completo puede generar logs grandes; el volumen debe controlarse en la plataforma y durante el piloto.
- Los errores técnicos devuelven mensajes públicos genéricos.
- La sanitización por nombre de campo no garantiza por sí sola el cumplimiento: cada dominio debe revisar qué objetos autoriza.

## Observabilidad

- Logger dedicado `exception.audit`.
- Evento JSON con microservicio, categoría, código, tabla, operación, correlación y objeto dentro de `metadata`.
- Stack trace configurable, siempre saneado antes de registrarse.
- Cabeceras configurables `X-Trace-Id` y `X-Correlation-Id` para correlación entre servicios.
- Los dashboards, métricas y alertas se implementan en la plataforma receptora.

## Decisiones técnicas vigentes

- Se usa anotación más API programática para cubrir HTTP, jobs, mensajería y tareas asíncronas.
- El nombre de tabla es explícito; no se analiza SQL ni texto de excepciones.
- Una instancia de excepción solo se registra una vez mediante un registro de claves débiles.
- La autoconfiguración usa `@ConditionalOnMissingBean` para admitir componentes personalizados.
- Los IDs entrantes se validan para impedir saltos de línea, valores excesivos o inyección en logs.
- Los ámbitos de traza restauran el MDC anterior y `wrap` transporta el ID a tareas asíncronas.
- La versión inicial mantiene todo en un artefacto; se dividirá en `core` y `starter` solo si aparecen consumidores sin Spring.

## Riesgos y pendientes

- Validar compatibilidad exacta con las versiones de Spring Boot de los servicios consumidores.
- Definir el esquema final del evento y su política de evolución.
- Integrar IDs de OpenTelemetry de forma automática si MDC no los contiene.
- Decidir si cada categoría requiere severidad diferente, métricas o alertas.
- Añadir pruebas de integración con los backends de logging y stacks de los entornos consumidores.
