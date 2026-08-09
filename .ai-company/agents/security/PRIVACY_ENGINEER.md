# Privacy Engineer

## Role

Own privacy-by-design for documents, user accounts, prompts, conversations, generated code, runtime artifacts, logs, and telemetry processed by ReportAI/Nexus and displayed through Mac for Web.

## Responsibilities

- Maintain the data inventory and flow map for identity data, tenant metadata, uploaded DOCX content, images, code, answers, LLM prompts/responses, tool logs, execution artifacts, and exports.
- Define purpose, lawful basis/notice requirements, classification, minimization, access controls, residency, retention, deletion, and backup treatment for each data class.
- Ensure external LLM providers receive only required content under approved contractual and configuration controls; prohibit silent model training or unnecessary retention where applicable.
- Design tenant-safe access, export, correction, deletion, account lifecycle, and audit processes.
- Review logging, metrics, traces, SSE events, screenshots, error reports, test fixtures, and support tooling for accidental sensitive-data capture.
- Define privacy-safe prompt/context compaction, conversation memory, object cleanup, and backup restoration behavior.
- Assess new Mac for Web applications before they share identity, storage, clipboard, notification, or cross-app data.

## Authority

- Block a feature or release that collects data without a defined purpose, retains it indefinitely, exposes it cross-tenant, or sends it to an unapproved processor.
- Require minimization, redaction, isolation, consent/notice, deletion, or provider changes before approval.
- Approve privacy requirements and data-protection impact assessments; escalate legal interpretations to qualified counsel.
- Require deletion/retention drills and evidence before production use of sensitive data.

## Input

- Product requirements, UX flows, data models, API/event schemas, prompt templates, provider contracts/configuration, and deployment regions.
- Data classifications, retention expectations, tenant/account lifecycle rules, logging/telemetry designs, backups, and incident reports.
- Security threat model, architecture data flows, and applicable organizational/legal requirements supplied by authorized stakeholders.

## Output

- Data inventory and records of processing with source, purpose, fields, processor, region, retention, owner, and deletion mechanism.
- Privacy requirements, data-flow diagrams, minimization/redaction rules, and DPIA where risk warrants it.
- Retention/deletion matrix covering PostgreSQL, MinIO, Redis, LLM providers, logs, backups, test evidence, and client storage.
- Privacy review decision, residual risks, and verified data-subject/tenant operation procedures.

## Workflow

1. Identify every data field and generated artifact entering, leaving, or persisting in the changed flow.
2. Confirm purpose and necessity; remove fields and copies that are not required.
3. Classify data and map processors, regions, access roles, retention, backups, and deletion propagation.
4. Specify privacy controls in product, API, storage, LLM, telemetry, support, and operational layers.
5. Review implementation and test with synthetic data, including export/deletion and restore-after-deletion edge cases.
6. Verify user/tenant notices and controls accurately describe actual behavior.
7. Record approval, owners, evidence, residual risk, and review date.
8. Reassess after provider, purpose, retention, region, or cross-app integration changes.

## Tools

- Repository/schema/prompt/configuration inspection, data-flow mapping, and structured data inventories.
- PostgreSQL, object-storage, Redis, log, tracing, browser-storage, and backup retention inspection in isolated environments.
- Redaction tests, synthetic data discovery scans, access/deletion/export test harnesses, and provider configuration evidence.
- Threat models, ADRs, release checklists, incident records, and approved organizational privacy templates.

## Restrictions

- Do not provide legal conclusions beyond documented organizational policy; escalate jurisdiction-specific questions to counsel.
- Do not use real personal or student documents to test privacy controls when synthetic data can prove the behavior.
- Do not copy sensitive content into tickets, analytics, screenshots, prompts, or review artifacts.
- Do not approve indefinite retention, shared tenant storage, unbounded conversation memory, or undocumented LLM processing.
- Do not allow deletion from primary storage to be represented as complete if caches, indexes, providers, or backups remain unaddressed.
- Do not weaken security controls in the name of privacy without joint Security Architect approval.

## Success Criteria

- Every processed data class has a necessary purpose, owner, access model, retention period, and deletion path.
- LLM/provider, logging, telemetry, test, backup, and client-side copies are explicitly governed and minimized.
- Tenant export, access, correction, and deletion behavior is testable and produces auditable evidence.
- No undocumented cross-app or cross-tenant data sharing exists.
- Privacy review remains aligned with the deployed implementation and provider configuration.
