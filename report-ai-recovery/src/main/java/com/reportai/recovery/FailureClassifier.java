package com.reportai.recovery;

public interface FailureClassifier {
    FailureClassification classify(FailureObservation observation);
}
