package com.reportai.recovery;

import com.reportai.common.contract.CommandId;
import com.reportai.common.tenant.TenantId;
import java.util.Objects;

public record RecoveryCommand(CommandId commandId, String idempotencyKey, String recoveryId, TenantId tenantId,
                              long expectedVersion, RecoveryAction action, RecoveryPayload payload, String payloadDigest) {
    public RecoveryCommand { commandId=Objects.requireNonNull(commandId,"commandId"); idempotencyKey=text(idempotencyKey,"idempotencyKey",128); recoveryId=text(recoveryId,"recoveryId",256); tenantId=Objects.requireNonNull(tenantId,"tenantId"); if(expectedVersion<0)throw new IllegalArgumentException("expectedVersion must not be negative"); action=Objects.requireNonNull(action,"action"); payload=Objects.requireNonNull(payload,"payload"); payloadDigest=text(payloadDigest,"payloadDigest",128); if(!payloadDigest.equals(payloadDigest(payload)))throw new IllegalArgumentException("payload digest mismatch"); }
    public RecoveryCommand(CommandId id,String idem,String recovery,TenantId tenant,long version,RecoveryAction action,RecoveryPayload payload){this(id,idem,recovery,tenant,version,action,payload,digest(payload));}
    public static String payloadDigest(RecoveryPayload payload){
        try { var md=java.security.MessageDigest.getInstance("SHA-256"); return java.util.HexFormat.of().formatHex(md.digest(Objects.toString(payload,"<none>").getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e){ throw new IllegalStateException(e); }
    }
    static String digest(RecoveryPayload payload){ return payloadDigest(payload); }
    private static String text(String v,String f,int max){Objects.requireNonNull(v,f);String n=v.strip();if(n.isBlank()||n.length()>max||n.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException(f+" is invalid");return n;}
}
