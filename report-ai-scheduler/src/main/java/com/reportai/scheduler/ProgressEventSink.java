package com.reportai.scheduler;

@FunctionalInterface
public interface ProgressEventSink {
    void publish(ProgressEvent event);

    static ProgressEventSink noOp() {
        return event -> { };
    }
}
