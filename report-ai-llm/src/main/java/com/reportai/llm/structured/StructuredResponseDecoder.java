package com.reportai.llm.structured;

/** Provider-neutral port; JSON/YAML decoding belongs to an infrastructure adapter. */
@FunctionalInterface
public interface StructuredResponseDecoder<T> {
    T decode(String content);
}
