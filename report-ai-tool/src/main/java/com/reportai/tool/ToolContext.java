package com.reportai.tool;

import com.reportai.common.tenant.TenantId;

public record ToolContext(String taskId, TenantId tenantId, String workspaceId) {
}
