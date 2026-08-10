package com.reportai.recovery;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.EventId;
import com.reportai.common.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

public record RecoveryEvent(EventId eventId, CommandId commandId, String idempotencyKey, String recoveryId, TenantId tenantId,
                            RecoveryAction action, RecoveryEventType type, RecoveryState from, RecoveryState to,
                            long version, String snapshotId, String payloadDigest, Instant occurredAt) {
    public RecoveryEvent { eventId=Objects.requireNonNull(eventId,"eventId"); commandId=Objects.requireNonNull(commandId,"commandId"); idempotencyKey=text(idempotencyKey,"idempotencyKey"); recoveryId=text(recoveryId,"recoveryId"); tenantId=Objects.requireNonNull(tenantId,"tenantId"); action=Objects.requireNonNull(action,"action"); type=Objects.requireNonNull(type,"type"); from=Objects.requireNonNull(from,"from"); to=Objects.requireNonNull(to,"to"); if(version<1)throw new IllegalArgumentException("version must be positive"); snapshotId=text(snapshotId,"snapshotId"); payloadDigest=text(payloadDigest,"payloadDigest"); occurredAt=Objects.requireNonNull(occurredAt,"occurredAt"); }
    private static String text(String v,String f){Objects.requireNonNull(v,f);String n=v.strip();if(n.isBlank()||n.length()>256||n.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(f+" is invalid");return n;}
}
