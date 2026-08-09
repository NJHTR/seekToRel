package com.reportai.review.precheck;

import com.reportai.common.contract.*;
import com.reportai.common.time.TimeSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class ProgramPrecheckEngine {
    private final TimeSource timeSource;
    public ProgramPrecheckEngine() { this(TimeSource.systemUtc()); }
    public ProgramPrecheckEngine(TimeSource timeSource) { this.timeSource = Objects.requireNonNull(timeSource, "timeSource"); }
    public PrecheckResult precheck(PrecheckRequest request) { return check(request); }
    public PrecheckResult check(PrecheckRequest request) {
        Objects.requireNonNull(request, "request"); Instant now = timeSource.now();
        String normalized = normalize(request.input()); Sha256Digest original = Sha256Digest.ofText(request.input()); Sha256Digest normalizedDigest = Sha256Digest.ofText(normalized);
        List<PrecheckFinding> findings = new ArrayList<>();
        if (!request.inputSnapshot().contentDigest().equals(original)) findings.add(finding(PrecheckStage.NORMALIZATION,"snapshot.digest",PrecheckSeverity.ERROR,"SNAPSHOT_STALE","Input snapshot does not match supplied input",55));
        if (request.input().isBlank()) findings.add(finding(PrecheckStage.NORMALIZATION,"input.non_blank",PrecheckSeverity.ERROR,"INPUT_BLANK","Input is empty",0));
        if (request.input().length() > request.contextBudget().maxChars() * 4) findings.add(finding(PrecheckStage.FORMAT_SECURITY,"input.max_size",PrecheckSeverity.CRITICAL,"INPUT_TOO_LARGE","Input exceeds the permitted size",80));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("../") || lower.contains("prompt injection") || lower.contains("-----begin private key-----") || lower.contains("<script")) findings.add(finding(PrecheckStage.FORMAT_SECURITY,"input.policy",PrecheckSeverity.CRITICAL,"POLICY_BLOCK","Input contains a disallowed pattern",90));
        if (!Set.of("text/plain","application/json","application/xml","docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(request.format().toLowerCase(Locale.ROOT))) findings.add(finding(PrecheckStage.FORMAT_SECURITY,"format.supported",PrecheckSeverity.ERROR,"FORMAT_UNSUPPORTED","Input format is unsupported",45));
        if (!request.schemaVersion().matches("[0-9]+(?:\\.[0-9]+){0,2}")) findings.add(finding(PrecheckStage.SCHEMA,"schema.version",PrecheckSeverity.ERROR,"SCHEMA_INVALID","Schema version is invalid",40));
        if (now.isAfter(request.policyExpiresAt())) findings.add(finding(PrecheckStage.RISK,"policy.expired",PrecheckSeverity.CRITICAL,"POLICY_EXPIRED","Policy or rule pack is expired",80));
        int missing=0,mismatch=0,cross=0,expired=0,resolved=0;
        for (PrecheckEvidence e : request.evidence()) { if (!e.tenantId().equals(request.tenantId())) { cross++; continue; } if (!e.available()) { missing++; continue; } if (now.isAfter(e.expiresAt())) { expired++; continue; } if (!e.schemaVersion().equals(request.schemaVersion())) { mismatch++; continue; } resolved++; }
        EvidenceResolution evidence = new EvidenceResolution(request.evidence().size(),resolved,missing,mismatch,cross,expired);
        if (missing > 0) findings.add(finding(PrecheckStage.EVIDENCE,"evidence.available",PrecheckSeverity.WARNING,"EVIDENCE_MISSING","Required evidence is unavailable",30));
        if (mismatch > 0) findings.add(finding(PrecheckStage.EVIDENCE,"evidence.schema",PrecheckSeverity.ERROR,"EVIDENCE_MISMATCH","Evidence schema does not match input",50));
        if (cross > 0) findings.add(finding(PrecheckStage.EVIDENCE,"evidence.tenant",PrecheckSeverity.CRITICAL,"EVIDENCE_TENANT","Evidence belongs to another tenant",90));
        if (expired > 0) findings.add(finding(PrecheckStage.EVIDENCE,"evidence.expiry",PrecheckSeverity.ERROR,"EVIDENCE_EXPIRED","Evidence has expired",45));
        if (request.context().size() > request.contextBudget().maxItems()) findings.add(finding(PrecheckStage.CONTEXT,"context.max_items",PrecheckSeverity.WARNING,"CONTEXT_TRUNCATED","Context was reduced to the configured item budget",10));
        var selected = request.context().stream().sorted(Comparator.comparingInt(ContextCandidate::priority).reversed().thenComparing(ContextCandidate::key)).limit(request.contextBudget().maxItems()).filter(c -> c.text().length() <= request.contextBudget().maxChars()).toList();
        var bounded = new ArrayList<ContextCandidate>(); int chars=0,tokens=0; for (ContextCandidate c:selected) { int ct=c.text().length(), tk=(ct+3)/4; if(chars+ct>request.contextBudget().maxChars()||tokens+tk>request.contextBudget().maxTokens()) continue; bounded.add(c); chars+=ct; tokens+=tk; }
        Sha256Digest projectionDigest=Sha256Digest.ofText(CanonicalJson.encode(bounded.stream().map(c->Map.of("key",c.key(),"text",c.text(),"priority",c.priority(),"evidence",c.evidence().digest().value())).toList()));
        ContextProjection projection=new ContextProjection(bounded,chars,tokens,projectionDigest);
        int score=Math.min(100,findings.stream().mapToInt(PrecheckFinding::scoreImpact).sum()); boolean ambiguous=findings.stream().anyMatch(f->f.severity()==PrecheckSeverity.WARNING)&&score<80; RiskAssessment risk=RiskAssessment.from(score,ambiguous);
        PrecheckDecision decision; PrecheckRoute route;
        boolean hardBlock = cross > 0 || findings.stream().anyMatch(f -> f.code().equals("POLICY_BLOCK"));
        if (hardBlock) { decision=PrecheckDecision.ISOLATE; route=PrecheckRoute.ISOLATE; }
        else if (request.priorInputDigests().contains(normalizedDigest)) { decision=PrecheckDecision.DEDUPLICATE; route=PrecheckRoute.DEDUPLICATE; }
        else if (missing>0) { decision=PrecheckDecision.WAITING_FOR_INPUT; route=PrecheckRoute.WAITING_FOR_INPUT; } else if (mismatch>0 || expired>0 || findings.stream().anyMatch(f->f.code().equals("SNAPSHOT_STALE"))) { decision=PrecheckDecision.RECOVERY; route=PrecheckRoute.RECOVERY; } else if (score>=80) { decision=PrecheckDecision.REJECT; route=PrecheckRoute.REJECT; } else if (ambiguous) { decision=PrecheckDecision.HUMAN_REVIEW; route=PrecheckRoute.HUMAN_REVIEW; } else { decision=PrecheckDecision.ACCEPT; route=PrecheckRoute.AGENT; }
        findings.sort(Comparator.naturalOrder());
        String id=normalizedDigest.value().substring(0,16)+"-"+request.rulePack().digest().value().substring(0,16);
        return new PrecheckResult(1,id,request.tenantId(),request.taskId(),request.operation(),request.agentId(),request.inputSnapshot(),original,normalizedDigest,request.rulePack(),findings,evidence,risk,projection,route,decision,now);
    }
    private static String normalize(String value) { return value.replace("\r\n","\n").replace('\r','\n').lines().map(String::stripTrailing).reduce((a,b)->a+"\n"+b).orElse("").strip(); }
    private static PrecheckFinding finding(PrecheckStage s,String rule,PrecheckSeverity sev,String code,String msg,int score){ return new PrecheckFinding(s,rule,sev,code,msg,List.of(),score); }
}
