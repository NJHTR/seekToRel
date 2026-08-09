# Architecture

ReportAI is a modular monolith with ports for document parsing, LLM providers,
tools, runtime, storage and workflow. PostgreSQL is authoritative for tenant,
task, workflow and audit state; MinIO stores binaries; Redis is coordination and
distributed rate limiting. Agents may only cause side effects through tools.
