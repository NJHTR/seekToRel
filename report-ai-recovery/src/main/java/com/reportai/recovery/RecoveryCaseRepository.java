package com.reportai.recovery;

import com.reportai.common.tenant.TenantId;
import java.util.List;
import java.util.Optional;

public interface RecoveryCaseRepository {
    RecoveryAttempt create(RecoveryAttempt attempt);
    Optional<RecoveryAttempt> find(String recoveryId, TenantId tenantId);
    AppendResult append(RecoveryAttempt expected, RecoveryAttempt next, RecoveryEvent event);
    List<RecoveryEvent> events(String recoveryId, TenantId tenantId);
    default Optional<RecoveryEvent> findByIdempotency(String id, TenantId tenant, String key) { return events(id,tenant).stream().filter(e->e.idempotencyKey().equals(key)).findFirst(); }
    record AppendResult(RecoveryAttempt state, RecoveryEvent event, boolean duplicate) { public AppendResult { if(state==null)throw new NullPointerException("state"); } public AppendResult(RecoveryAttempt state,boolean duplicate){this(state,null,duplicate);} }
}
