package com.reportai.auth.jwt;

/** Claim names accepted by the JWT adapter. Keep this explicit to avoid ambiguous claim mapping. */
public record JwtClaimNames(
        String tenant,
        String permissions) {

    public JwtClaimNames {
        tenant = requireText(tenant, "tenant");
        permissions = requireText(permissions, "permissions");
    }

    public static JwtClaimNames defaults() {
        return new JwtClaimNames("tenant_id", "permissions");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(name + " claim name is invalid");
        }
        return value;
    }
}
