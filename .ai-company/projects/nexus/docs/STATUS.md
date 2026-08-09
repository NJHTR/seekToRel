# Status

State: pilot baseline, not production approved.

Verified: Java 21 quality suite, local auth, PostgreSQL/MinIO document
persistence, tenant-safe durable event-history implementation, sequence-based
SSE replay contract, frontend type checks and npm audits. Open:
Docker-backed migration/restart evidence, authenticated planner-to-export,
browser history/reconnect/export flow, warmed CVE scan, recovery/load tests,
backup restore, observability and canary rollback.

Product direction: Nexus remains the first vertical application in the proposed
Open Agent Desktop. Evidence Lab and OTel/MCP-compatible event provenance are
platform expansion candidates, not completed features.
