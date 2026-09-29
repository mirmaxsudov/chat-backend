---
name: chat-clone-backend
description: Develop, debug, review, and test this Java 21 Spring Boot chat backend. Use for work on its REST APIs, JWT security, JPA persistence, WebSocket/STOMP messaging, presence, attachments, MinIO storage, TUS uploads, media previews, or Maven build.
---

# Chat Clone Backend

Work as a staff-level Spring Boot engineer in this repository. Make focused changes that fit the existing design, preserve API compatibility unless the user requests otherwise, and verify behavior at the narrowest useful level.

## Establish context

Before editing, inspect the relevant production code, neighboring implementations, configuration, and tests. Treat `pom.xml` and the active `application*.yml` files as authoritative for framework versions and configuration. Do not assume APIs from an older Spring Boot release; this project uses Java 21 and Spring Boot 4.x.

Follow established repository conventions when they are internally consistent. Do not reorganize the current layered package structure as incidental cleanup. Prefer a small, coherent diff over speculative abstraction or unrelated modernization.

## Implement Spring components

- Use constructor injection for required dependencies and keep them `private final`; Lombok-generated constructors are acceptable where already established.
- Keep controllers focused on HTTP or protocol concerns, services on business rules and transaction boundaries, and repositories on persistence.
- Use request and response DTOs at external boundaries. Do not expose JPA entities directly.
- Apply Jakarta Bean Validation to input DTOs and `@Valid` at entry points. Reuse the global exception handling and response conventions rather than inventing endpoint-specific error shapes.
- Use `@ConfigurationProperties` for grouped configuration. Keep secrets and environment-specific values out of source control.
- Use MapStruct and QueryDSL in the style already present. Check generated-source implications before changing mapped fields or query types.
- Use SLF4J parameterized logging. Never log credentials, tokens, verification codes, private message content, or sensitive object metadata.

## Preserve correctness boundaries

Place `@Transactional` on service operations that form a single consistency boundary. Keep remote storage, media processing, and realtime publication outside long database transactions where practical. For events that consumers must observe only after a successful commit, preserve or add after-commit publication semantics.

For security changes, verify both authentication and authorization. REST rules, method-level access, WebSocket handshake/channel interception, resource ownership, and attachment access are distinct boundaries; success at one does not imply success at another. Retain BCrypt-compatible password handling and JWT validation behavior.

For chat, presence, and WebSocket work, account for reconnects, duplicate delivery, concurrent sessions, ordering, and idempotency. Do not treat in-memory presence state as durable data.

For attachments, MinIO, TUS, and media previews:

- Validate size, type, ownership, offsets, ranges, and object keys at the trust boundary.
- Keep upload finalization and retryable background work idempotent.
- Prevent path traversal and avoid loading unbounded files into memory.
- Clean temporary chunks only when they are stale and no active upload can still reference them.
- Preserve HTTP range and cache semantics when changing download or preview behavior.

## Test and verify

Add or update tests for observable behavior, especially authorization failures, transaction timing, repository queries, protocol edge cases, and retry/idempotency behavior. Match the existing test style:

- focused unit tests for isolated business logic;
- MVC/security tests for controller contracts;
- repository or application integration tests when persistence, transactions, or framework wiring matters.

Use the Maven wrapper so the repository controls the Maven version. On Windows run `./mvnw.cmd`; on Unix-like systems run `./mvnw`. Start with a targeted test such as `./mvnw.cmd -Dtest=ClassName test`, then run `./mvnw.cmd test` when the change has broader impact. If required infrastructure is unavailable, report the exact verification that could and could not run.

Before finishing, inspect the diff for accidental configuration changes, leaked secrets, compatibility breaks, and generated or temporary files. Summarize the behavior changed and the verification performed.
