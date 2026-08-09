# Architecture Decision Record Index

The detailed historical ADRs for ReportAI live in `.ai/DECISIONS.md`. This index
records company-level decisions and prevents the organization layer from
silently contradicting the product architecture.

## ADR-COMPANY-001 - Modular product portfolio

**Decision:** Mac for Web is the integration shell; Nexus is a product app; the
other applications are independently owned portfolio projects with explicit
iframe or API contracts.

**Why:** It lets the portfolio grow without turning the shell into a monolith,
while keeping the ReportAI backend reusable for future document workflows.

**Consequences:** Every embedded app needs a manifest, health/error state and
responsive window contract. Cross-app data access requires an explicit API and
permission review.

## ADR-COMPANY-002 - Company documents are gates, not instructions to bypass code review

**Decision:** Agents may parallelize implementation only after a task is ready,
and all merges still require independent review, QA and security evidence.

**Why:** Parallel output without ownership and gates creates inconsistent APIs,
duplicate abstractions and unreviewed security risk.

## ADR-COMPANY-003 - External credentials stay outside the repository

**Decision:** LLM/API credentials enter through process environment or a secret
manager. A key previously exposed in chat is treated as compromised and must be
rotated.

**Why:** Source-control and transcript retention cannot be assumed private.
