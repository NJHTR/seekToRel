package com.reportai.review.precheck;

public record RiskAssessment(int score, Level level, boolean ambiguous) {
    public enum Level { LOW, MEDIUM, HIGH, CRITICAL }
    public RiskAssessment { if (score < 0 || score > 100) throw new IllegalArgumentException("score out of range"); if (level == null) throw new NullPointerException("level"); }
    public static RiskAssessment from(int score, boolean ambiguous) { return new RiskAssessment(score, score >= 80 ? Level.CRITICAL : score >= 55 ? Level.HIGH : score >= 25 ? Level.MEDIUM : Level.LOW, ambiguous); }
}
