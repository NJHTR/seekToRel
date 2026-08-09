# Release Policy

## Promotion gates

`development -> review -> integration -> security -> staging -> canary -> production`

Each promotion has a versioned release record containing source revision,
artifact checksums, migration list, test results, security scan, operational
readiness, known risks and rollback command. The Release Manager may stop a
release when any P0 gate is missing.

## Rollback invariant

Rollback must not delete durable task, workflow, document or object metadata.
Forward-compatible migrations are preferred; destructive schema changes require
an explicit backup, restore rehearsal and a two-step deployment plan.

## Current Nexus gate

The authoritative production checklist is
`docs/production-release-checklist.md`. As of the bootstrap date, authenticated
LLM upload-to-export evidence, warmed CVE scan, backup/restore drill and staging
observability/rollback evidence remain open.
