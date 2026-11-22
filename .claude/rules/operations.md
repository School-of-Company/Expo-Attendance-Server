---
paths:
  - "src/main/kotlin/team/startup/expo/global/**"
  - "src/main/resources/**"
  - "load-test/**"
  - "src/main/kotlin/team/startup/expo/**/scheduler/**"
  - "src/main/kotlin/team/startup/expo/**/*Scheduler.kt"
  - "src/main/kotlin/team/startup/expo/**/repository/**"
---

# Operations

- Periodic jobs take a PostgreSQL advisory lock (`AdvisoryJobLock`, `pg_try_advisory_xact_lock`) so instances do not run the same work; the paper QR re-check locks per expo and skips an expo another instance is processing. The lock lives as long as the transaction, so a crashed instance releases it. Do not add ShedLock.
- Expo lock: only expo deletion takes the exclusive expo lock (`DeletedExpoRepository.lockExpo`); every other write (walk-in, preregister session PUT/DELETE/field registration, cancellation guard acquire, field visit, QR issue) takes the shared one (`lockExpoShared`, via `lockAndRequireAlive`) and then checks `tb_deleted_expo`, so writes never wait for each other, only for a deletion. Writes that must be ordered among themselves take a narrower key after it: walk-in per category, preregister session per participant, QR serials per category. Order stays expo → participant → registration → trainee.
- `CANCELLATION_ABORTED_RETENTION_DAYS` (default 0 = off) deletes `tb_cancellation_aborted` rows older than N days. Nothing else is deleted by age (proposal, not decided: `docs/data-retention.md`).
- Rollback: Flyway migrations are never reverted; keep new tables and columns when rolling back to an older build. V9 dropped NOT NULL on `tb_preregister_session.session_id`, so builds before v0.2.0 may fail on such rows.
- Health: `/actuator/health/liveness` and `/actuator/health/readiness` are public GET. Readiness includes only `readinessState` and `db`; never add User/Expo/Application.
- Load: entry scans hold a DB connection while they call other services under the guard lock, so throughput is pool size ÷ downstream latency. `DB_POOL_SIZE` (30), `DB_POOL_CONNECTION_TIMEOUT_MS` (3000) and `TRANSACTION_TIMEOUT` (15s) bound it; a saturated pool or timed-out transaction answers 503, not 500. Reproduce with `load-test/` (JMeter + stub services).
- Stability: `server.shutdown=graceful` (`SHUTDOWN_TIMEOUT`, default 20s) so a deploy finishes in-flight scans. `/actuator/prometheus` is on the service port (the gateway routes no `/actuator`; `METRICS_PORT` moves it to a separate internal port) and Eureka metadata `prometheus.scrape/port/path` lets the shared Prometheus find it. Metrics: `attendance.circuit.breaker.*` gauges, `attendance.downstream.calls{service,outcome}` (every call to another service, inside `callService`), `attendance.guard.lock.wait{kind=scan|cancel}`, `attendance.qr.entry.pending` (paper QR entries waiting for the period re-check). Tags carry only the service name and outcome, never IDs, phone numbers or bodies. Alert rules live in Expo-Monitoring.
