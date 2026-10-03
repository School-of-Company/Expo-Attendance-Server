---
name: security-checklist
description: Verify security vulnerabilities — hardcoded secrets, injection, token validation, credential masking, sensitive logging, and authorization checks. Run before merging any auth or API-related changes.
---

# Security Checklist

## Verification Items

### 1. Hardcoded Secrets

- [ ] No API key, secret, or password literal in source?
- [ ] Loaded from environment variables / `application.yaml` placeholders, never a literal?

```bash
grep -rniE "(password|secret|api_?key|token|credential)s?[[:space:]]*[:=][[:space:]]*['\"][^'\"]{6,}" --include="*.kt" --include="*.java" src/
grep -rniE "(password|secret|api_?key|token)" --include="*.yml" --include="*.yaml" --include="*.properties" . --exclude-dir=build

# env files that must never be committed
git ls-files | grep -E '^\.env'
```

### 2. SQL Injection

- [ ] Using JPA/QueryDSL parameter binding — never string-concatenated JPQL/native SQL?

```bash
grep -rn '@Query(' --include="*.kt" -A3 src/ | grep -E '\+|\$\{'
```

### 3. JWT Verification

- [ ] Verifying signature with an expected algorithm (reject `alg: none`)?
- [ ] Checking expiration and issuer/audience claims, not just decoding?

```bash
grep -rniE "(parseClaims|verify|decode).*(jwt|token)" --include="*.kt" src/
```

### 4. Credential Handling

- [ ] Passwords hashed (BCrypt or equivalent), never stored plaintext?
- [ ] Tokens/keys masked or omitted in API responses?

### 5. Logging

- [ ] No passwords, tokens, or personal data in log lines?

```bash
grep -rniE "log(ger)?\.(debug|info|warn|error).*(password|token|secret)" --include="*.kt" src/
```

### 6. Authorization

- [ ] Every endpoint needing auth actually enforces it — `SecurityConfig` matcher, `@PreAuthorize`, or equivalent?
- [ ] Ownership checked, so changing an id in the path can't reach another user's resource?

```bash
grep -rn "@RestController" --include="*.kt" -l src/
```

Compare the controller list against `SecurityConfig`'s `authorizeHttpRequests` matchers — an endpoint with
no matching rule falls through to `anyRequest().denyAll()` here, so check for that being the actual intent.

## Report

For each category, mark ✅ Pass / ❌ Fail / ⚠️ Needs Review with a one-line justification, and list any
failing grep matches as `file:line`.
