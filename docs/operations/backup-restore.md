# ReportAI backup and restore runbook

This runbook is for staging/production operators. It does not change the
application or any unrelated middleware. The local SSH-tunnel ports used for
development (`127.0.0.1:15432` PostgreSQL and `127.0.0.1:19000` MinIO) are
examples only; production jobs must use private service endpoints and a secret
manager.

## PostgreSQL logical backup

Use a dedicated backup identity with read access to the ReportAI database.
Write the dump to an encrypted backup volume, never to the repository:

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dump = "report-ai-$stamp.dump"
pg_dump --format=custom --no-owner --no-privileges `
  --dbname="$env:REPORT_AI_DB_URL" `
  --username="$env:REPORT_AI_DB_USERNAME" `
  --file="$dump"
pg_restore --list "$dump" | Select-Object -First 5
Get-FileHash "$dump" -Algorithm SHA256
```

Record the release version, Flyway schema version, dump timestamp and SHA-256
digest in the backup inventory. Upload only through the approved encrypted
backup destination with a retention policy and deletion protection.

## PostgreSQL restore drill

Restore into a newly provisioned isolated database (never over the live
database), then validate schema and representative tenant-scoped reads:

```powershell
createdb --host="$env:RESTORE_DB_HOST" --port="$env:RESTORE_DB_PORT" `
  --username="$env:RESTORE_DB_USERNAME" "$env:RESTORE_DB_NAME"
pg_restore --clean --if-exists --no-owner --no-privileges `
  --dbname="$env:RESTORE_DB_URL" "$dump"
psql "$env:RESTORE_DB_URL" -c "SELECT version, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
psql "$env:RESTORE_DB_URL" -c "SELECT count(*) FROM tenant;"
```

The drill passes only when Flyway history is consistent, representative
documents/tasks can be read, tenant ownership constraints still hold, and the
restored application starts with the production profile. Keep the database
until the evidence is reviewed; remove it only after explicit operator
approval.

## MinIO object backup

Use an access key limited to the ReportAI bucket and enable TLS. The `mc mirror`
destination must be a versioned, encrypted bucket in a separate failure
domain:

```powershell
mc alias set reportai "$env:REPORT_AI_OBJECT_STORAGE_ENDPOINT" `
  "$env:REPORT_AI_OBJECT_STORAGE_ACCESS_KEY" "$env:REPORT_AI_OBJECT_STORAGE_SECRET_KEY"
mc mirror --overwrite --remove=false reportai/report-ai backup/report-ai
mc find backup/report-ai --name '*' --exec 'mc stat --json {}'
```

Do not use `--remove` for the first backup or restore drill. Preserve object
versions and record the source bucket, destination bucket, timestamp, object
count and failed-copy list.

## MinIO restore verification

Restore into an isolated bucket, then verify a sample from every tenant prefix:

```powershell
mc mirror --overwrite backup/report-ai reportai-restore/report-ai
mc du --recursive reportai-restore/report-ai
mc stat --json reportai-restore/report-ai/documents/<tenant-hash>/<document-id>.docx
```

The application must be pointed at the isolated bucket for a smoke test. The
source object SHA-256 and streamed resolver checksum must match the metadata
stored in PostgreSQL. Never repoint production to a restore bucket during a
drill.

## Evidence record

For each drill archive:

- release/build identifier and Flyway version;
- backup and restore timestamps, operator and environment;
- PostgreSQL and object counts before/after;
- SHA-256/checksum verification output;
- failed objects or SQL errors and remediation;
- time to restore and the measured recovery point/time objectives;
- approval and next scheduled drill date.
