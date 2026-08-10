package com.reportai.scheduler.plan;

/** Read-only aggregate of persisted reservations, including the all-zero idle state. */
public record ResourceUsage(long cpuMillis, long memoryMiB, long storageMiB, int slots) {
    public ResourceUsage {
        if (cpuMillis < 0 || memoryMiB < 0 || storageMiB < 0 || slots < 0) {
            throw new IllegalArgumentException("resource usage must not be negative");
        }
    }

    public ResourceUsage plus(ResourceRequest request) {
        return new ResourceUsage(Math.addExact(cpuMillis, request.cpuMillis()),
                Math.addExact(memoryMiB, request.memoryMiB()),
                Math.addExact(storageMiB, request.storageMiB()),
                Math.addExact(slots, request.slots()));
    }

    public boolean canAdd(ResourceRequest request, ResourceCapacity capacity) {
        try {
            ResourceUsage candidate = plus(request);
            return candidate.cpuMillis() <= capacity.cpuMillis()
                    && candidate.memoryMiB() <= capacity.memoryMiB()
                    && candidate.storageMiB() <= capacity.storageMiB()
                    && candidate.slots() <= capacity.slots();
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    public boolean within(ResourceCapacity capacity) {
        return cpuMillis <= capacity.cpuMillis() && memoryMiB <= capacity.memoryMiB()
                && storageMiB <= capacity.storageMiB() && slots <= capacity.slots();
    }

    public static ResourceUsage zero() {
        return new ResourceUsage(0, 0, 0, 0);
    }
}
