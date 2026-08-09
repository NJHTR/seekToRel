package com.reportai.common.id;

/** Domain port; implementations may use UUID/ULID/Snowflake according to deployment needs. */
public interface IdGenerator {
    String nextId();
}
