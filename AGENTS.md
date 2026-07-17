# AGENTS

## Contexto

Este repositorio contiene una librería transversal de tratamiento y logging de excepciones para microservicios Java. Toda modificación debe priorizar compatibilidad, seguridad de datos y adopción gradual, porque un cambio puede afectar a decenas de aplicaciones.

## Perfiles y supervisión experta

- Arquitectura: experiencia en librerías Java, Spring Boot, compatibilidad binaria y plataformas de microservicios; supervisión por arquitectura de plataforma.
- Implementación: experiencia en Java 17, Spring Boot 3, AOP, Jackson y SLF4J; supervisión por un desarrollador senior Java.
- QA: experiencia en pruebas unitarias, autoconfiguración e integración entre versiones; supervisión por QA de plataforma.
- Operaciones: experiencia en observabilidad, logs estructurados, MDC, OpenTelemetry y despliegue masivo; supervisión por SRE/operaciones.
- Seguridad: experiencia en privacidad, secretos y minimización de datos en logs; supervisión por seguridad de aplicaciones.

Cada frente debe ser revisado por un experto de su mismo campo antes de consolidar cambios sensibles.

## Custodia del contexto funcional

Siempre deben existir dos responsables del contexto funcional:

1. Responsable de plataforma de microservicios, experto en adopción y compatibilidad transversal.
2. Responsable de observabilidad y operación, experto en diagnóstico, correlación y explotación de logs.

Ambos deben acordar objetivos, restricciones, riesgos y esquema del evento antes de desarrollar una capacidad nueva. Los desacuerdos se documentan y se escalan al propietario de la plataforma.

## Decision funcional acordada

- Funcionalidad o iniciativa: starter común de excepciones y logs.
- Agente funcional 1: responsable de plataforma de microservicios.
- Agente funcional 2: responsable de observabilidad y operación.
- Supervisores expertos: arquitectura Java, SRE y seguridad de aplicaciones.
- Objetivo acordado: unificar el diagnóstico de errores de base de datos, negocio y conectividad en microservicios Spring Boot.
- Reglas de negocio confirmadas: incluir microservicio, categoría, causa, tabla y operación cuando se conozcan; admitir contexto del objeto de forma optativa.
- Restricciones aceptadas: Java 17, Spring Boot 3, integración por dependencia, sin acoplamiento a entidades ni bases de datos concretas.
- Riesgos funcionales asumidos: los servicios deben declarar tabla/contexto; el nombre no siempre puede deducirse automáticamente.
- Enfoque elegido: starter autoconfigurable, clasificación extensible, anotación y API programática, logs JSON y saneado.
- Alternativas descartadas: parsear SQL o mensajes del driver por ser frágil; registrar siempre el objeto por riesgo de privacidad; exigir una clase base por aumentar acoplamiento.
- Impacto esperado en arquitectura: API pública pequeña y estable, autoconfiguración reemplazable y contrato de evento versionable.
- Estado del acuerdo: aprobado para MVP; pendiente de validación en el entorno de destino antes del despliegue.

## Especialización concreta

- Dominio: manejo transversal de fallos y observabilidad de microservicios.
- Stack: Java 17, Maven, Spring Boot 3, Spring AOP, Jackson y SLF4J.
- Arquitectura: librería starter autoconfigurable y extensible por interfaces/beans.
- Persistencia: ninguna.
- Integraciones: plataforma de logs del consumidor, MDC, Spring MVC y APIs de acceso/integración.

## Reglas de trabajo

- Trabajar en ramas `feature/<descripcion>` y no directamente sobre `main`.
- Mantener compatibilidad binaria de la API pública o elevar la versión mayor.
- No añadir campos obligatorios al evento sin estrategia de compatibilidad.
- No introducir ninguna vía de configuración que desactive, sustituya o reduzca el saneado obligatorio.
- Mantener el objeto relacionado dentro de `metadata` y sanear también mensajes y stack traces.
- Evitar dependencias obligatorias de un ORM, driver, proveedor o backend de logging concreto.
- Mantener `README.md` y `ARQUITECTURA.md` sincronizados con el código.
- Añadir pruebas proporcionales a clasificación, serialización, autoconfiguración y compatibilidad modificadas.
- Verificar con `mvn clean verify` antes de integrar.
- Preparar una pull request contra la rama principal con alcance, riesgos y verificación.

## Criterios de calidad

- La adopción básica requiere solo la dependencia y `spring.application.name`.
- Los fallos del propio logging no deben ocultar el error original.
- Los mensajes HTTP no deben revelar detalles técnicos.
- La librería debe permitir reemplazar sus decisiones predeterminadas sin hacer fork.
- La documentación debe reflejar lo que existe, no capacidades futuras.
