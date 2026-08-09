package com.reportai.auth.model;

/**
 * Fine-grained permissions used by application services. A token resolver is responsible for
 * mapping verified claims to this allow-list; arbitrary permission strings from a request are
 * never accepted.
 */
public enum Permission {
    DOCUMENT_READ,
    DOCUMENT_WRITE,
    DOCUMENT_DELETE,
    TASK_CREATE,
    TASK_READ,
    TASK_CONTROL,
    WORKFLOW_READ,
    WORKFLOW_CONTROL,
    EXECUTION_READ,
    EXPORT_READ,
    TENANT_ADMIN,
    PLATFORM_ADMIN
}
