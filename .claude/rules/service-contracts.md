---
paths:
  - "src/main/kotlin/team/startup/expo/**/presentation/**"
  - "src/main/kotlin/team/startup/expo/global/client/**"
  - "src/main/kotlin/team/startup/expo/global/security/**"
  - "src/main/kotlin/team/startup/expo/global/swagger/**"
  - "docs/**"
---

# Service contracts

Full request/response shapes, status codes and issue links: **`docs/service-contracts.md`** (field visits: `docs/field-visits.md`, admin SSE: `docs/admin-data-change-events.md`). Change a contract by opening an issue first, and read the other side's `develop` code instead of assuming it. All `/internal/**` calls carry `X-Internal-Token`. Feign uses one circuit breaker per service; 404/409 are results, anything else becomes 503.

| Direction | Service | Summary |
|---|---|---|
| We call | User | resolve phone → ID, record entry (`POST /internal/entries`), entry status, trainee link, code verify, companion QR, cancellation status, names, entry cards |
| We call | Expo | expo period, standard/training programs, preregister session times |
| We call | Application | program application `applied` |
| We provide | Expo | program attendance list, cleanup on program/expo deletion |
| We provide | Application | preregister sessions, field visits, cancellation guard (owner/admin), cancellation status, companion QR, program attendance summary |
| We provide | Report | entry records for the admin Excel |
| We provide | Gateway clients | entry/exit/program/paper QR scans, walk-in counter, live status summary and SSE, paper QR token admin |

**API docs**: springdoc serves Swagger UI at `/attendance/swagger-ui.html` and the spec at `/attendance/v3/api-docs` (under the gateway's `/attendance` prefix, forwarded unchanged), only when `SWAGGER_ENABLED=true` (default `false`; SecurityConfig opens `GET` on the docs paths only then). `/internal/**` is documented only with `SWAGGER_INCLUDE_INTERNAL=true` (default `false`), which also documents `X-Internal-Token`. The spec pins `servers` to `[{"url": "/"}]` (the gateway rewrites `Host`; the instance address must not leak) and declares `bearerAuth` (HTTP bearer JWT, checked by the gateway) on every non-internal operation for the gateway's unified docs page. The gateway needs `publicPaths` for `GET /attendance/v3/api-docs` and `GET /attendance/swagger-ui` (and sub-paths). Document each endpoint's real error statuses with `@ApiError(status, description)` and every DTO field with `@field:Schema`.

**Config**: `INTERNAL_TOKEN`, `USER_INTERNAL_TOKEN`, `EXPO_INTERNAL_TOKEN`, `APPLICATION_INTERNAL_TOKEN` must be 32+ characters or the service will not start. Every environment variable and its default is in `src/main/resources/application.yaml`. `/internal/**` is not routed by the gateway, so it is unreachable from outside.

## Auth

- The gateway verifies the JWT and forwards `X-User-Id` and, when the token has a role, `X-User-Role` (it strips client-sent copies). Do not parse tokens or trust those headers outside the gateway path: admin-only routes check `X-User-Role` (`GatewayRoleAuthenticationFilter`), so the service must be reachable only through the gateway. Service-to-service calls bypass the gateway and use `X-Internal-Token` on `/internal/**`.
