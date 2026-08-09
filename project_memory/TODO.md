# Active TODO

- [ ] Warm and pass OWASP Java dependency/CVE scan; record findings or approved exceptions.
- [x] Add and locally execute the opt-in Testcontainers integration profile (PostgreSQL, MinIO, Redis, Docker runtime); 9/9 container tests pass locally, while CI evidence remains open.
- [x] Add in-process DOCX upload -> parse -> task -> review -> export acceptance test; real object-store/CI execution evidence remains open.
- [ ] Verify scheduler restart recovery, duplicate delivery and concurrent idempotency.
- [ ] Verify cross-tenant authorization and object-key isolation under concurrency.
- [x] Pin PostgreSQL, MinIO, Redis and runtime images by digest.
- [x] Verify non-root user, dropped capabilities and runtime sandbox policy in the Docker integration path; CI evidence remains required.
- [ ] Run authenticated xfcxb `gpt-5.6-terra` smoke and DOCX task with `reasoning_effort=high` using a rotated secret.
- [ ] Restart the backend and repeat the full DOCX task with a new task ID after injecting the rotated provider secret; the previous task is terminal after the former 60-second coder timeout.
- [ ] Execute PostgreSQL/MinIO backup and restore drills and archive evidence.
- [ ] Connect production metrics, logs, dashboards, alerts and canary rollback evidence.
- [ ] Decide on optional WebSocket/OCR/search/browser scope after P0/P1 evidence.
