package com.reportai.review.precheck;

import com.reportai.common.contract.CanonicalJson;
import com.reportai.common.contract.Sha256Digest;
import java.util.*;

public record RulePack(String id, String version, Sha256Digest digest, String policyVersion,
                       List<RuleDescriptor> rules) {
    public RulePack {
        id = text(id, "id", 64); version = text(version, "version", 32);
        digest = Objects.requireNonNull(digest, "digest"); policyVersion = text(policyVersion, "policyVersion", 32);
        rules = rules == null ? List.of() : List.copyOf(rules);
        if (rules.isEmpty() || rules.size() > 256) throw new IllegalArgumentException("rule pack must contain 1..256 rules");
        if (rules.stream().map(RuleDescriptor::id).distinct().count() != rules.size()) throw new IllegalArgumentException("duplicate rule id");
        var computed = Sha256Digest.ofText(CanonicalJson.encode(rules.stream().map(r -> Map.of(
                "id", r.id(), "stage", r.stage().name(), "severity", r.severity().name(),
                "description", r.description(), "scoreWeight", r.scoreWeight())).toList()));
        if (!computed.equals(digest)) throw new IllegalArgumentException("rule pack digest mismatch");
    }
    public static RulePack of(String id, String version, String policyVersion, List<RuleDescriptor> rules) {
        var value = rules == null ? List.<RuleDescriptor>of() : List.copyOf(rules);
        var digest = Sha256Digest.ofText(CanonicalJson.encode(value.stream().map(r -> Map.of(
                "id", r.id(), "stage", r.stage().name(), "severity", r.severity().name(),
                "description", r.description(), "scoreWeight", r.scoreWeight())).toList()));
        return new RulePack(id, version, digest, policyVersion, value);
    }
    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field); var n = value.strip();
        if (n.isBlank() || n.length() > max || n.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException(field + " is invalid");
        return n;
    }
}
