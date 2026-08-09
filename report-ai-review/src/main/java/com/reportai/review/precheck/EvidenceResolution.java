package com.reportai.review.precheck;

public record EvidenceResolution(int requested, int resolved, int missing, int mismatched, int crossTenant, int expired) {
    public EvidenceResolution { if (requested < 0 || resolved < 0 || missing < 0 || mismatched < 0 || crossTenant < 0 || expired < 0) throw new IllegalArgumentException("evidence counts invalid"); }
    public boolean complete() { return missing == 0 && mismatched == 0 && crossTenant == 0 && expired == 0; }
}
