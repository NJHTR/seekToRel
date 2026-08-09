# Backend

Current API contracts cover authentication, document upload, task creation,
authenticated SSE progress and review-gated export. Next backend work is a
tenant-authorized, bounded, keyset-paginated task-event history endpoint and a
durable event repository without weakening the existing SSE adapter.
