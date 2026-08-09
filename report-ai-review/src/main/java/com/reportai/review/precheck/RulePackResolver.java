package com.reportai.review.precheck;

import java.util.Optional;

@FunctionalInterface
public interface RulePackResolver {
    Optional<RulePack> resolve(String id, String version);
}
