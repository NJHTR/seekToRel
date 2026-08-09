package com.reportai.application.workflow;

import com.reportai.api.workflow.WorkflowControlRequest;
import com.reportai.api.workflow.WorkflowResponse;
import com.reportai.api.workflow.WorkflowStartRequest;
import com.reportai.auth.model.Permission;
import com.reportai.auth.model.TenantPrincipal;
import com.reportai.auth.port.AuthorizationPort;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.exception.BusinessException;
import com.reportai.common.tenant.TenantContext;
import com.reportai.workflow.WorkflowEngine;
import com.reportai.workflow.WorkflowInstance;
import org.springframework.stereotype.Service;

/** Tenant- and permission-aware HTTP boundary around the workflow port. */
@Service
public final class WorkflowHttpApplicationService {
    private final WorkflowEngine workflowEngine;
    private final AuthorizationPort authorization;

    public WorkflowHttpApplicationService(WorkflowEngine workflowEngine, AuthorizationPort authorization) {
        this.workflowEngine = workflowEngine;
        this.authorization = authorization;
    }

    public WorkflowResponse start(WorkflowStartRequest request) {
        authorized(Permission.WORKFLOW_CONTROL);
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "workflow start request is required");
        }
        return response(workflowEngine.start(request.definitionId(), request.taskId()));
    }

    public WorkflowResponse get(String workflowId) {
        authorized(Permission.WORKFLOW_READ);
        validateId(workflowId, "workflowId");
        return response(workflowEngine.get(workflowId));
    }

    public WorkflowResponse pause(String workflowId, WorkflowControlRequest request) {
        authorized(Permission.WORKFLOW_CONTROL);
        return response(workflowEngine.pause(validId(workflowId, "workflowId"), version(request)));
    }

    public WorkflowResponse resume(String workflowId, WorkflowControlRequest request) {
        authorized(Permission.WORKFLOW_CONTROL);
        return response(workflowEngine.resume(validId(workflowId, "workflowId"), version(request)));
    }

    public WorkflowResponse cancel(String workflowId, WorkflowControlRequest request) {
        authorized(Permission.WORKFLOW_CONTROL);
        return response(workflowEngine.cancel(validId(workflowId, "workflowId"), version(request)));
    }

    private TenantPrincipal authorized(Permission permission) {
        TenantPrincipal principal = authorization.requireAuthenticated();
        if (!principal.tenantId().equals(TenantContext.require())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "tenant context is invalid");
        }
        authorization.requirePermission(permission);
        return principal;
    }

    private static long version(WorkflowControlRequest request) {
        if (request == null || request.expectedVersion() < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "expectedVersion must not be negative");
        }
        return request.expectedVersion();
    }

    private static String validId(String value, String name) {
        validateId(value, name);
        return value;
    }

    private static void validateId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " is invalid");
        }
    }

    private static WorkflowResponse response(WorkflowInstance instance) {
        return new WorkflowResponse(instance.id(), instance.definitionId(), instance.definitionVersion(),
                instance.taskId(), instance.currentNodeId(), instance.status().name(), instance.state(), instance.version());
    }
}
