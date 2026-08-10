package com.reportai.scheduler.plan;

/** Maximum resources available to one scheduler store. */
public record ResourceCapacity(long cpuMillis, long memoryMiB, long storageMiB, int slots) {
    public ResourceCapacity {
        if (cpuMillis <= 0 || cpuMillis > ResourceRequest.MAX_CPU_MILLIS
                || memoryMiB <= 0 || memoryMiB > ResourceRequest.MAX_MEMORY_MIB
                || storageMiB < 0 || storageMiB > ResourceRequest.MAX_STORAGE_MIB
                || slots <= 0 || slots > ResourceRequest.MAX_SLOTS) {
            throw new IllegalArgumentException("resource capacity is outside bounded limits");
        }
    }

    public static ResourceCapacity defaults() {
        return new ResourceCapacity(100_000, 65_536, 1_048_576, 64);
    }

    public boolean includes(ResourceRequest request) {
        return request.fitsWithin(this);
    }
}
