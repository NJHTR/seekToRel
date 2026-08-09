# Backend contract

Java 21/Spring Boot modules use ports and adapters, PostgreSQL/Flyway for
durable state, MinIO for bytes, Redis for coordination and Docker for untrusted
runtime execution. All writes are validated, authorized, observable and
idempotent where retries are possible.
