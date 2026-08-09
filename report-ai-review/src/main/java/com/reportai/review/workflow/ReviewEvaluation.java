package com.reportai.review.workflow;

import com.reportai.common.contract.EvaluationPacket;
import com.reportai.review.precheck.PrecheckResult;

import java.util.Objects;

/** Evaluation plus the exact precheck receipt that authorized it. */
public record ReviewEvaluation(EvaluationPacket packet, PrecheckResult precheck) {
    public ReviewEvaluation {
        packet = Objects.requireNonNull(packet, "packet");
        precheck = Objects.requireNonNull(precheck, "precheck");
    }
}
