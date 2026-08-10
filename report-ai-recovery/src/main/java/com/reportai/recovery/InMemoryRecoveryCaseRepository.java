package com.reportai.recovery;

import com.reportai.common.tenant.TenantId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Test adapter with atomic CAS and append-only idempotency semantics. */
public final class InMemoryRecoveryCaseRepository implements RecoveryCaseRepository {
    private final Map<String, RecoveryAttempt> cases = new ConcurrentHashMap<>();
    private final Map<String, List<RecoveryEvent>> log = new ConcurrentHashMap<>();
    @Override public RecoveryAttempt create(RecoveryAttempt attempt) { Objects.requireNonNull(attempt); if(cases.putIfAbsent(key(attempt.recoveryId(),attempt.tenantId()),attempt)!=null) throw new RecoveryConflictException("recovery already exists"); log.put(key(attempt.recoveryId(),attempt.tenantId()),Collections.synchronizedList(new ArrayList<>())); return attempt; }
    @Override public Optional<RecoveryAttempt> find(String id,TenantId tenant){return Optional.ofNullable(cases.get(key(id,tenant)));}
    @Override public AppendResult append(RecoveryAttempt expected, RecoveryAttempt next, RecoveryEvent event) { String k=key(expected.recoveryId(),expected.tenantId()); synchronized(lock(k)){ RecoveryAttempt current=cases.get(k); if(current==null)throw new RecoveryConflictException("unknown recovery"); Optional<RecoveryEvent> duplicate=findByIdempotency(expected.recoveryId(),expected.tenantId(),event.idempotencyKey()); if(duplicate.isPresent()){if(!duplicate.get().payloadDigest().equals(event.payloadDigest())) throw new RecoveryConflictException("idempotency payload digest conflict"); return new AppendResult(current,duplicate.get(),true);} if(!current.equals(expected)||!next.tenantId().equals(expected.tenantId())||!event.tenantId().equals(expected.tenantId())||next.version()!=expected.version()+1||event.version()!=next.version()||!event.snapshotId().equals(next.sourceSnapshot().snapshotId().value())||!validTransition(current,next,event)) throw new RecoveryConflictException("stale or invalid recovery transition"); if(!next.sourceSnapshot().snapshotId().equals(expected.sourceSnapshot().snapshotId())) throw new RecoveryConflictException("snapshot changed"); cases.put(k,next); log.get(k).add(event); return new AppendResult(next,event,false); }}
    @Override public List<RecoveryEvent> events(String id,TenantId tenant){List<RecoveryEvent> e=log.get(key(id,tenant));return e==null?List.of():List.copyOf(e);}
    public RecoveryAttempt replay(String id,TenantId tenant){RecoveryAttempt state=find(id,tenant).orElseThrow(); RecoveryState prior=RecoveryState.PENDING; long expectedVersion=1; for(RecoveryEvent e:events(id,tenant)){if(!e.tenantId().equals(tenant)||!e.snapshotId().equals(state.sourceSnapshot().snapshotId().value())||e.version()!=expectedVersion||e.from()!=prior||!e.recoveryId().equals(id)||!validType(e))throw new RecoveryConflictException("tampered recovery event"); prior=e.to(); expectedVersion++;} if (!events(id,tenant).isEmpty() && prior!=state.state()) throw new RecoveryConflictException("recovery state does not match event replay"); return state;}
    private static boolean validType(RecoveryEvent event) {
        return switch (event.action()) {
            case START_ATTEMPT -> event.type() == RecoveryEventType.ATTEMPT_STARTED;
            case SUBMIT_INPUT -> event.type() == RecoveryEventType.ATTEMPT_COMPLETED || event.type() == RecoveryEventType.INPUT_REQUESTED;
            case APPROVE_DOWNLOAD -> event.type() == RecoveryEventType.DOWNLOAD_APPROVAL_GRANTED;
            case REJECT_DOWNLOAD -> event.type() == RecoveryEventType.DOWNLOAD_APPROVAL_DENIED;
            case DIAGNOSE_CODE -> event.type() == RecoveryEventType.CODE_DIAGNOSED;
            case REPAIR_DEPENDENCY -> event.type() == RecoveryEventType.DEPENDENCY_REPAIR_PLANNED || event.type() == RecoveryEventType.DEPENDENCY_REPAIR_VALIDATED;
            case RUN_OCR -> event.type() == RecoveryEventType.OCR_COMPLETED;
            case EXTRACT_CHART -> event.type() == RecoveryEventType.CHART_EXTRACTED;
            case RESOLVE_CONFLICT -> event.type() == RecoveryEventType.CONFLICT_RESOLVED;
            case ESCALATE -> event.type() == RecoveryEventType.ESCALATED;
            default -> true;
        };
    }
    private static boolean validTransition(RecoveryAttempt current, RecoveryAttempt next, RecoveryEvent event) {
        if (!validType(event) || event.from() != current.state() || event.to() != next.state()) return false;
        return switch (event.action()) {
            case START_ATTEMPT -> current.state() == RecoveryState.PENDING && next.state() == RecoveryState.RUNNING;
            case APPROVE_DOWNLOAD, REJECT_DOWNLOAD -> current.state() == RecoveryState.WAITING_FOR_APPROVAL;
            case SUBMIT_INPUT -> current.state() == RecoveryState.WAITING_FOR_INPUT;
            case COMPLETE -> current.state() == RecoveryState.RUNNING && next.state() == RecoveryState.COMPLETED;
            case ESCALATE -> next.state() == RecoveryState.ESCALATED;
            default -> current.state() == RecoveryState.RUNNING;
        };
    }
    private static String key(String id,TenantId t){return t.value()+":"+id;}
    private final Map<String,Object> locks=new ConcurrentHashMap<>(); private Object lock(String k){return locks.computeIfAbsent(k,x->new Object());}
}
