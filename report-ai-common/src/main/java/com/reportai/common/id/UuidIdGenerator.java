package com.reportai.common.id;

import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
