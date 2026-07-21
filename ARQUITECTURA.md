# ARQUITECTURA

## Objetivo del sistema

Proporcionar un componente transversal para que los microservicios Java clasifiquen, registren y traduzcan excepciones de forma uniforme sin acoplar la librería a una base de datos, ORM, dominio o proveedor de conectividad concretos.

## Problema que resuelve

Centraliza el formato de los logs y el tratamiento HTTP de errores de autenticación, base de datos, negocio, conectividad e inesperados. Permite incorporar contexto conocido por la aplicación, como tabla, operación y objeto afectado.

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
- Los fallos de autenticación se clasifican como `AUTH` y se traducen a HTTP 401 con un mensaje genérico; los fallos de autorización 403 permanecen separados.
- El objeto seleccionado se incorpora en `metadata` hasta los límites seguros de profundidad y nodos; el saneado es obligatorio y no desactivable.
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
- `TracePropagationFilter`: genera el ID interno y solo acepta un ID externo cuando se configura explícitamente una entrada de confianza.
- `TracePropagationInterceptor`: añade el ID únicamente a hosts salientes permitidos.

## Flujo principal

1. El filtro genera un ID nuevo o, si el ingress es de confianza y se ha habilitado, reutiliza un ID entrante válido; después abre el ámbito MDC.
2. Los clientes HTTP salientes propagan ese mismo ID solo a hosts incluidos explícitamente en la allowlist.
3. Una operación anotada o un manejador HTTP recibe una excepción.
4. El reporter evita registrar dos veces la misma instancia y clasifica su cadena causal.
5. Recoge nombre del microservicio, traza, causa raíz y contexto proporcionado.
6. Convierte los metadatos y el objeto a JSON, aplica límites de profundidad/nodos y reglas obligatorias de enmascarado.
7. Sanea también mensajes y stack trace antes de emitir el evento mediante `exception.audit`.
8. El manejador devuelve la respuesta y el filtro cierra el ámbito, restaurando el MDC previo.

## Impacto del consenso funcional

- Decisión funcional: lograr una integración homogénea en cualquier microservicio Spring Boot sin conocer su dominio concreto.
- Módulos afectados: un único starter autocontenido con API y autoconfiguración.
- Contrato principal: `ExceptionLogEvent`; sus campos deben evolucionar de forma compatible.
- Restricciones: Java 17, Spring Boot 3.5, bajo acoplamiento y protección de datos.
- Riesgos mitigados: dependencias mantenidas, filtrado obligatorio de secretos y patrones sensibles, límites de tamaño, respuesta HTTP sin causa interna y stack trace saneado y opt-in.

## Estructura técnica del repositorio

```text
/
|-- AGENTS.md
|-- ARQUITECTURA.md
|-- README.md
|-- pom.xml
`-- src/
    |-- main/java/platform/exceptionloggin/
    |-- main/resources/META-INF/spring/
    `-- test/java/platform/exceptionloggin/
```

## Datos y contratos principales

- `ExceptionLogEvent`: esquema del evento de observabilidad.
- `FailureContext`: datos opcionales aportados por el microservicio.
- `ApiError`: respuesta HTTP sin información técnica sensible.
- `BusinessException`: código de negocio, detalle interno, mensaje público explícito y estado HTTP 4xx.

La librería no persiste datos.

## Integraciones externas

- SLF4J como fachada de logging; el microservicio decide el backend y transporte.
- MDC para `correlationId` y `traceId`, gestionado mediante ámbitos restaurables.
- Spring MVC cuando está disponible.
- `RestClient` y `RestTemplate` construidos por Spring Boot para propagación HTTP saliente.
- Excepciones Spring JDBC/DAO, cliente REST, Spring Security, JAAS y respuestas HTTP 401 para la clasificación predeterminada. La detección de Spring Security se realiza sin convertirlo en dependencia obligatoria para los consumidores.

## Despliegue y operación

- Runtime: Java 17 y Spring Boot 3.5.x.
- Distribución: `io.github.erazort01:java-logger-interceptor` en GitHub Packages mediante una GitHub Release inmutable.
- Configuración: propiedades `exception-logging.*`; no usa secretos propios.
- Despliegue: junto con cada microservicio consumidor.
- Evolución: versiones semánticas y BOM o parent POM cuando el entorno consumidor lo requiera.

## Seguridad

- Lista interna obligatoria de campos sensibles, aplicada recursivamente sin distinguir mayúsculas.
- Detección adicional de correos, teléfonos, identificadores, JWT, credenciales Basic/Bearer, cookies, claves privadas y asignaciones sensibles en texto libre.
- Los servicios solo pueden añadir reglas mediante `additional-sensitive-fields`; no pueden eliminar ni desactivar las obligatorias.
- Los textos, la profundidad y el número de nodos se limitan antes del log; el volumen agregado debe controlarse también en la plataforma.
- Los errores técnicos devuelven mensajes públicos genéricos.
- El advice HTTP y los stack traces están desactivados por defecto.
- Las trazas externas no son de confianza por defecto y la propagación saliente requiere hosts permitidos.
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
- Los IDs entrantes están deshabilitados por defecto y, al habilitarlos, se validan para impedir saltos de línea, valores excesivos o inyección en logs.
- El reporter y el aspecto son best-effort: un fallo propio se adjunta como suprimido y nunca sustituye la excepción de aplicación.
- La cadena causal se recorre con identidad y un máximo de 64 elementos para evitar ciclos y consumo ilimitado.
- Los ámbitos de traza restauran el MDC anterior y `wrap` transporta el ID a tareas asíncronas.
- La versión inicial mantiene todo en un artefacto; se dividirá en `core` y `starter` solo si aparecen consumidores sin Spring.

## Riesgos y pendientes

- Mantener una matriz de compatibilidad dentro de la línea Spring Boot 3.5.x.
- Definir el esquema final del evento y su política de evolución.
- Integrar IDs de OpenTelemetry de forma automática si MDC no los contiene.
- Decidir si cada categoría requiere severidad diferente, métricas o alertas.
- Añadir pruebas de integración con los backends de logging y stacks de los entornos consumidores.
- Mantener los avisos y metadatos de Apache License 2.0 en cada distribución.
