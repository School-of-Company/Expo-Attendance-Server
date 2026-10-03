# Expo Attention Server

Attendance (참여) service of the Expo MSA. Kotlin 2.3 / Spring Boot 4.1, Gradle, Java 21, PostgreSQL + Flyway.

## Scope

- Port of the v1 monolith (`Expo-Server`) `attendance` domain: expo entry scan (`PreEnterScanQrCode`), standard/training program attendance. Match v1; adapt only what the split forces.
- New in v2 (decided 2026-10-03, pending confirmation with the 신청 owner): on-site **paper QR** tokens. This service issues the tokens, records entry when one is scanned at the door, and answers Form-Server's "was this token entered?" check. Form-Server stores the survey answers; it never issues tokens.
- Not owned here: SMS, pre-application (신청) creation and the pre-registration QR, form/survey definitions and answers (Form-Server), participants and trainees (User-Server), expo and program data (Expo-Expo-Server).

## Boundaries

- Domains: `domain/{attendance,qr}`. Other services' data (expo, program, participant, survey) is referenced by ID only: no FK, no local entity. Reach them through Feign.
- Entry is recorded once per QR/token. A second scan must be rejected, as `PreEnterScanQrCode` does in v1.

## Auth

- The gateway verifies the JWT and forwards only `X-User-Id`. Do not parse tokens or trust that header outside the gateway path; service-to-service calls bypass the gateway and need their own authentication (undecided).
- Never commit keys or secrets.

## Conventions

- Follow the `kotlin-spring-arch` and `api-design` skills in `.claude/skills/`.
- Entities follow Expo-Expo-Server style: `@field:` annotations, `Long? = null` ids.
- Throw `ExpectedException(status, message)` directly; no subclasses.

## Workflow

- Run `./gradlew ktlintCheck build` before every commit. The context test uses Testcontainers, so Docker must be running.
- Branch from `origin/develop`; never commit to `main` or `develop`.
- Open PRs with the `write-pr` skill and commit with the `git-commit` skill. The PR title format `[scope] description` is enforced by CI (`ci.yml`).
- No AI co-author trailer on commits or PRs.
