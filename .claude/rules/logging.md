---
paths:
  - "src/main/**/*.kt"
  - "src/main/resources/logback-spring.xml"
  - "src/main/resources/application.yaml"
---

# Logging

The Expo log standard, shared by every service (Spring and Nest) so the shared Loki in Expo-Monitoring labels them the same way:

- Every stdout line is one JSON object (`logback-spring.xml`, `LogstashEncoder`) with at least `@timestamp` (ISO-8601/RFC3339), `level` (uppercase `TRACE`/`DEBUG`/`INFO`/`WARN`/`ERROR`), `service` (the Eureka app name, `spring.application.name`), `module` and `message`. Other keys (MDC `requestId`/`expoId`/`participantId`, `logger_name`, `stack_trace`) may follow. Nothing else goes to stdout: the Spring banner is off and Logback status warnings must not appear.
- The pipeline reads only these keys and has no per-service fallback. If logs do not show up or fields do not match, fix the service's output, never add parsing fallbacks to the pipeline.
- `module` is derived from the logger name by `ModuleNameConverter`, never set at the call site: for `team.startup.expo.{domain|global}.<x>...` it is `<x>`, otherwise the logger's parent package. Nest services reuse the per-class logger context (`nestjs-pino` `renameContext: 'module'`).
- Never log request/response bodies, phone numbers, participant codes, tokens or URLs with ids.
- Every request carries `X-Request-Id` (MDC `requestId`; the gateway does not set one, so we accept a safe client value or generate it) and Feign calls forward it. `LOG_LEVEL_CLIENT=debug` temporarily shows circuit-open calls.
- Access log: `HttpAccessLogFilter` writes one line per request with logger `HttpAccess` (`module` = `HttpAccess`): `<METHOD> <route template, else the URI without query cut to 200 chars> status: <status> (<ms>ms)`, INFO below 400, WARN for 4xx, ERROR for 5xx and a client abort (logged as 499). It runs inside `RequestIdFilter` and outside the security chain, so 401/403 are logged too; `/actuator/**` and the API docs paths are skipped. Never add query strings, headers or bodies. `LOGGING_HTTP_ACCESS_ENABLED=false` turns it off.
- With the `local` profile the same JSON also goes to `${EXPO_LOG_DIR:-~/.expo/logs}/<service>/app.log` for the local Loki file tail. Production writes stdout only (pm2 captures it); add no log file settings there.
