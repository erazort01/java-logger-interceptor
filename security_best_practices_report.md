# Informe de seguridad — java-logger-interceptor

Fecha: 2026-07-19

Rama revisada: `feature/security-maven-publish`

Alcance: código Java, configuración Spring Boot, dependencias Maven, exposición HTTP, trazas, secretos y workflows de GitHub Actions.

## Resumen ejecutivo

Se corrigieron todos los hallazgos de severidad alta y media identificados en el alcance revisado. La versión inicial resolvía componentes afectados por 12 avisos de GitHub Advisory Database; la versión corregida resuelve 20 artefactos de ejecución y la consulta exacta de cada coordenada/versión devuelve 0 avisos conocidos.

La validación final ejecuta 31 pruebas, SpotBugs con esfuerzo máximo y umbral medio, y genera correctamente los artefactos binario, fuentes y Javadoc. También se simuló el ciclo Maven `deploy` contra un repositorio local sin publicar externamente.

## Metodología y límites

- Revisión manual de límites de confianza, sanitización, tratamiento de excepciones, trazas y auto-configuración.
- Resolución real del árbol Maven con `dependency:tree` y `dependency:list`.
- Consulta exacta por paquete y versión en GitHub Advisory Database para todos los 20 artefactos de ejecución.
- Análisis estático con SpotBugs 4.9.8.2 integrado en `verify`.
- Búsqueda de patrones de secretos tanto en el árbol actual como en el historial Git.
- Pruebas unitarias y de auto-configuración con `mvn clean verify`.

La comprobación adicional con OWASP Dependency-Check no pudo completar la actualización de NVD por respuestas HTTP 429 al no disponer de una clave NVD. Por tanto, el resultado de dependencias se apoya en GitHub Advisory Database y no constituye una garantía frente a avisos todavía no publicados o no indexados.

## Hallazgos corregidos

### SEC-001 — Alta — Dependencias con vulnerabilidades conocidas

**Estado:** corregido.

La línea base incluía Spring Boot 3.3.13, Spring Framework 6.1.21 y Jackson 2.17.3. Las consultas exactas devolvieron 12 avisos, entre ellos CVE-2026-40973, CVE-2025-41249, CVE-2025-41242 y cuatro CVE de Jackson Databind de severidad alta/media.

Se actualizaron y fijaron Spring Boot 3.5.15, Spring Framework 6.2.19 (gestionado por Boot) y Jackson 2.21.5. Véase `pom.xml:38-59`. La comprobación final de los 20 artefactos de ejecución devolvió 0 avisos.

Referencias principales:

- https://github.com/advisories/GHSA-wwpq-f5c3-7hvx
- https://github.com/advisories/GHSA-jmp9-x22r-554x
- https://github.com/advisories/GHSA-r936-gwx5-v52f
- https://github.com/advisories/GHSA-j3rv-43j4-c7qm
- https://github.com/advisories/GHSA-rmj7-2vxq-3g9f
- https://github.com/advisories/GHSA-hgj6-7826-r7m5
- https://github.com/advisories/GHSA-5jmj-h7xm-6q6v

### SEC-002 — Alta — Fuga de credenciales y datos personales en logs

**Estado:** corregido.

La sanitización anterior no cubría de forma suficiente Basic/Bearer, cookies, secretos entrecomillados o con espacios, JWT, claves PEM, teléfono ni identificadores españoles. Tampoco limitaba el tamaño o la profundidad de metadatos y activaba stack traces por defecto.

Se ampliaron los campos y patrones obligatorios en `ContextSanitizer.java:20-50`, se aplica sanitización a mensajes, causas, metadatos y stack traces en `Slf4jExceptionReporter.java:57-98`, y se añadieron límites de longitud, profundidad y nodos con valores seguros por defecto en `ExceptionLoggingProperties.java:21-25`. Los stack traces quedan desactivados por defecto.

Riesgo residual: la sanitización por patrones no sustituye una política DLP. Los consumidores deben añadir nombres de campo específicos mediante `additional-sensitive-fields` y evitar introducir secretos en mensajes de excepción.

### SEC-003 — Alta — Exposición de mensajes técnicos a clientes HTTP

**Estado:** corregido.

`BusinessException` separa ahora el mensaje interno del mensaje público y usa un texto público genérico si no se proporciona uno (`BusinessException.java:6-42`). El manejador valida los códigos públicos y nunca refleja por defecto el mensaje técnico (`GlobalExceptionHandler.java:18-45`).

### SEC-004 — Alta — Fallos del logger podían ocultar el error original o agotar recursos

**Estado:** corregido.

La notificación y su preparación son de mejor esfuerzo: los fallos no fatales del reporter se agregan como suprimidos sin reemplazar la excepción de negocio (`ReportingGuard.java:3-38`, `LogFailureAspect.java:16-34`). El recorrido de causas usa identidad, un máximo de 64 niveles y detección de ciclos (`ThrowableChain.java:10-30`). Los metadatos y stack traces tienen presupuestos explícitos.

### SEC-005 — Media — Suplantación y propagación indiscriminada de identificadores de traza

**Estado:** corregido.

Las trazas entrantes no se aceptan por defecto, se validan con una lista de caracteres y longitud acotada, y las salientes solo se propagan a hosts incluidos expresamente en la allowlist. Véanse `ExceptionLoggingProperties.java:18-26`, `DefaultTraceContext.java:22-70`, `TracePropagationFilter.java:16-39` y `TracePropagationInterceptor.java:12-50`.

Riesgo residual: al activar `accept-incoming-trace-ids`, el despliegue debe hacerlo únicamente detrás de un proxy o gateway de confianza que elimine cabeceras aportadas por clientes no confiables.

### SEC-006 — Media — Manejador HTTP global invasivo y transformación de errores 4xx en 500

**Estado:** corregido.

El manejador web es opt-in, usa la precedencia más baja y preserva respuestas 4xx de Spring como errores de solicitud genéricos sin registrarlos como fallos internos. Véanse `ExceptionLoggingProperties.java:15`, `ExceptionLoggingAutoConfiguration.java:103-120` y `GlobalExceptionHandler.java:16-58`.

### SEC-007 — Media — Interferencia con el ObjectMapper de la aplicación consumidora

**Estado:** corregido.

La auto-configuración se ordena después de `JacksonAutoConfiguration` y solo crea un `ObjectMapper` si el consumidor no aporta uno. El bloque servlet queda aislado por tipo de aplicación y presencia de clases. Véase `ExceptionLoggingAutoConfiguration.java:17-26` y `ExceptionLoggingAutoConfiguration.java:101-120`.

### SEC-008 — Baja — Endurecimiento insuficiente de build, secretos y supply chain

**Estado:** corregido.

- `.gitignore:6-13` excluye `.env`, `settings.xml`, claves y certificados privados.
- `pom.xml:236-252` integra SpotBugs en `verify`.
- Los workflows usan permisos mínimos, credenciales de checkout deshabilitadas y acciones fijadas por SHA.
- Dependabot revisa Maven y GitHub Actions semanalmente.
- El workflow de publicación comprueba que el tag de release coincide exactamente con una versión no SNAPSHOT.

## Preparación de publicación

El proyecto queda configurado como `io.github.erazort01:java-logger-interceptor:1.0.0`, con namespace Java `platform.exceptionloggin`, Apache License 2.0, `distributionManagement` para GitHub Packages, JAR de fuentes, JAR de Javadoc y workflow `Publish Java Package with Maven` activado al publicar una GitHub Release.

Paso pendiente antes de la primera publicación: subir la rama, fusionarla y publicar un release con tag exacto `v1.0.0`. No se realizó ningún push, release ni publicación externa durante esta revisión.

## Evidencia de verificación

- `mvn clean verify`: **BUILD SUCCESS**, 31 pruebas, 0 fallos, 0 errores.
- SpotBugs 4.9.8.2: **0 BugInstance**, 0 errores.
- Auditoría de dependencias de ejecución: **20 artefactos**, **0 avisos conocidos** en GitHub Advisory Database.
- `mvn deploy -DaltDeploymentRepository=local::file://...`: **BUILD SUCCESS**; POM, JAR, sources y Javadoc desplegados localmente.
- Acciones fijadas verificadas contra los tags remotos oficiales:
  - `actions/checkout@v7.0.0` → `9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0`
  - `actions/setup-java@v5.6.0` → `03ad4de0992f5dab5e18fcb136590ce7c4a0ac95`
