# ARQUITECTURA

## Objetivo del sistema

Proporcionar un componente transversal para que los microservicios Java clasifiquen, registren y traduzcan excepciones de forma uniforme sin acoplar la librería a una base de datos, ORM, dominio o proveedor de conectividad concretos.

## Problema que resuelve

Centraliza el formato de los logs y el tratamiento HTTP de errores de base de datos, negocio, conectividad e inesperados. Permite incorporar contexto conocido por la aplicación, como tabla, operación y objeto afectado.

No sustituye a una plataforma de observabilidad, a la instrumentación de trazas, a las políticas de reintentos ni a la gestión de alertas. Tampoco intenta extraer de forma automática y no fiable el nombre de una tabla a partir de SQL o mensajes del driver.

## Alcance actual

- Starter de Spring Boot con configuración automática.
- Clasificador predeterminado y punto de extensión para clasificadores corporativos.
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

## Flujo principal

1. Una operación anotada o un manejador HTTP recibe una excepción.
2. El reporter evita registrar dos veces la misma instancia y clasifica su cadena causal.
3. Recoge nombre del microservicio, MDC, causa raíz y contexto proporcionado.
4. Convierte los metadatos y el objeto completo a JSON y aplica reglas obligatorias de enmascarado.
5. Sanea también mensajes y stack trace antes de emitir el evento mediante `exception.audit`.
6. En HTTP, el manejador devuelve un error funcional o una respuesta técnica genérica.

## Impacto del consenso funcional

- Decisión funcional: lograr una integración homogénea en 120 microservicios sin conocer sus dominios concretos.
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
- MDC para `correlationId` y `traceId`.
- Spring MVC cuando está disponible.
- Excepciones Spring JDBC/DAO y cliente REST para la clasificación predeterminada.

## Despliegue y operación

- Runtime: Java 17 y Spring Boot 3.x.
- Distribución: artefacto Maven versionado en Nexus o Artifactory.
- Configuración: propiedades `exception-logging.*`; no usa secretos propios.
- Despliegue: junto con cada microservicio consumidor.
- Evolución: versiones semánticas y BOM/parent POM corporativo para controlar la adopción.

## Seguridad

- Lista interna obligatoria de campos sensibles, aplicada recursivamente sin distinguir mayúsculas.
- Detección adicional de correos, IBAN, tarjetas, JWT, cabeceras Bearer y asignaciones de credenciales en texto libre.
- Los servicios solo pueden añadir reglas mediante `additional-sensitive-fields`; no pueden eliminar ni desactivar las obligatorias.
- El objeto completo puede generar logs grandes; el volumen debe controlarse en la plataforma y durante el piloto.
- Los errores técnicos devuelven mensajes públicos genéricos.
- La sanitización por nombre de campo no garantiza por sí sola el cumplimiento: cada dominio debe revisar qué objetos autoriza.

## Observabilidad

- Logger dedicado `exception.audit`.
- Evento JSON con microservicio, categoría, código, tabla, operación, correlación y objeto dentro de `metadata`.
- Stack trace configurable, siempre saneado antes de registrarse.
- Los dashboards, métricas y alertas se implementan en la plataforma receptora.

## Decisiones técnicas vigentes

- Se usa anotación más API programática para cubrir HTTP, jobs, mensajería y tareas asíncronas.
- El nombre de tabla es explícito; no se analiza SQL ni texto de excepciones.
- Una instancia de excepción solo se registra una vez mediante un registro de claves débiles.
- La autoconfiguración usa `@ConditionalOnMissingBean` para admitir componentes corporativos.
- La versión inicial mantiene todo en un artefacto; se dividirá en `core` y `starter` solo si aparecen consumidores sin Spring.

## Riesgos y pendientes

- Validar compatibilidad exacta con las versiones de Spring Boot usadas por los 120 servicios.
- Definir el esquema corporativo final del evento y su política de evolución.
- Integrar IDs de OpenTelemetry de forma automática si MDC no los contiene.
- Decidir si cada categoría requiere severidad diferente, métricas o alertas.
- Añadir pruebas de integración con los backends de logging y stacks reales de la organización.
