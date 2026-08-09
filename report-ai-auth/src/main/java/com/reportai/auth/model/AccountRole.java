package com.reportai.auth.model;

/** Auditable account roles. Platform administration is intentionally distinct from tenant admin. */
public enum AccountRole {
    MEMBER,
    TENANT_ADMIN,
    PLATFORM_ADMIN
}
