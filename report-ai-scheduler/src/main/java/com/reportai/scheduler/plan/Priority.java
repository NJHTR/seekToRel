package com.reportai.scheduler.plan;

/** Bounded priority persisted with a node; larger values run first. */
public record Priority(int value) implements Comparable<Priority> {
    public static final int MIN = 0;
    public static final int MAX = 100;

    public Priority {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("priority must be between " + MIN + " and " + MAX);
        }
    }

    public static Priority of(int value) {
        return new Priority(value);
    }

    public static Priority normal() {
        return new Priority(50);
    }

    @Override
    public int compareTo(Priority other) {
        return Integer.compare(value, other.value);
    }
}
