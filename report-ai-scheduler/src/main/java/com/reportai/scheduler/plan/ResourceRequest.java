package com.reportai.scheduler.plan;

/** Finite resources reserved atomically with a lease. Units are CPU-millis, MiB, MiB and slots. */
public record ResourceRequest(long cpuMillis, long memoryMiB, long storageMiB, int slots) {
    public static final long MAX_CPU_MILLIS = 1_000_000L;
    public static final long MAX_MEMORY_MIB = 1_048_576L;
    public static final long MAX_STORAGE_MIB = 4_194_304L;
    public static final int MAX_SLOTS = 1_024;

    public ResourceRequest {
        if (cpuMillis < 0 || cpuMillis > MAX_CPU_MILLIS
                || memoryMiB < 0 || memoryMiB > MAX_MEMORY_MIB
                || storageMiB < 0 || storageMiB > MAX_STORAGE_MIB
                || slots < 0 || slots > MAX_SLOTS) {
            throw new IllegalArgumentException("resource request is outside bounded limits");
        }
    }

    public static ResourceRequest of(long cpuMillis, long memoryMiB, long storageMiB, int slots) {
        return new ResourceRequest(cpuMillis, memoryMiB, storageMiB, slots);
    }

    public boolean fitsWithin(ResourceCapacity capacity) {
        return cpuMillis <= capacity.cpuMillis() && memoryMiB <= capacity.memoryMiB()
                && storageMiB <= capacity.storageMiB() && slots <= capacity.slots();
    }

    public ResourceRequest plus(ResourceRequest other) {
        try {
            return new ResourceRequest(Math.addExact(cpuMillis, other.cpuMillis),
                    Math.addExact(memoryMiB, other.memoryMiB),
                    Math.addExact(storageMiB, other.storageMiB),
                    Math.addExact(slots, other.slots));
        } catch (ArithmeticException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("resource total exceeds bounded limits", ex);
        }
    }

    public static ResourceRequest zero() {
        return new ResourceRequest(0, 0, 0, 0);
    }
}
