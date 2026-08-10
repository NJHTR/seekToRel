package com.reportai.recovery;

import com.reportai.common.error.FailureClass;
import java.util.List;
import java.util.Objects;

/** Fail-closed routing policy. It uses only stable error facts, never exception text. */
public final class DeterministicFailureClassifier implements FailureClassifier {
    @Override public FailureClassification classify(FailureObservation observation) {
        Objects.requireNonNull(observation, "observation");
        FailureClass fc = observation.error().failureClass();
        FailureRoute route = switch (fc) {
            case INPUT_MISSING -> FailureRoute.WAIT_FOR_INPUT;
            case POLICY, AUTHENTICATION, AUTHORIZATION -> FailureRoute.POLICY_BLOCKED;
            case DEPENDENCY, CODE, OCR, DATA, CONFLICT, PROGRAM -> FailureRoute.AUTOMATIC_REPAIR;
            case RESOURCE, INFRASTRUCTURE -> observation.error().retryable() ? FailureRoute.AUTOMATIC_REPAIR : FailureRoute.ESCALATE;
            case CONTRACT -> FailureRoute.ESCALATE;
        };
        if (observation.error().code().contains("DOWNLOAD") && route == FailureRoute.AUTOMATIC_REPAIR) route = FailureRoute.WAIT_FOR_DOWNLOAD_APPROVAL;
        if (!observation.error().retryable() && route == FailureRoute.AUTOMATIC_REPAIR) route = FailureRoute.ESCALATE;
        return new FailureClassification(fc, route, 1.0, List.of("stable_failure_class", observation.error().code()), route == FailureRoute.POLICY_BLOCKED);
    }
}
