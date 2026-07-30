package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletenessAgentTest {
    @Test
    void rejectsMissingOutputs() {
        AgentResult result = new CompletenessAgent(List.of("plan", "code"))
                .execute(new AgentContext("t", new TenantId("tenant"), Map.of("plan", "ok"), null, null));
        assertEquals(AgentResult.Status.FAILED, result.status());
        assertEquals(List.of("code"), result.outputs().get("missingStateKeys"));
    }
}
