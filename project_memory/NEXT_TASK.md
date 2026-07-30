# Next Task

Priority order for the next autonomous iterations:

1. Run the new opt-in integration profile in Docker-enabled CI and archive its evidence.
2. Run the DOCX upload-to-export acceptance path against real PostgreSQL/MinIO in CI and archive the evidence.
3. Validate the OWASP dependency scan on a warmed cache (CI requires `NVD_API_KEY` for predictable runtime).
4. Execute and record the backup/restore, scheduler-recovery and staging observability drills using `docs/operations/backup-restore.md`.
5. Reassess optional WebSocket, OCR, search and browser tools only after the release gates above have evidence.

Current release blockers are tracked in `docs/production-release-checklist.md` and `.ai/TODO.md`.
