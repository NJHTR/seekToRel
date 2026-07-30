package com.reportai.llm.structured;

import java.util.Objects;

public final class StructuredResponsePipeline<T> {
    private final StructuredResponseDecoder<T> decoder;
    private final StructuredResponseValidator<T> validator;

    public StructuredResponsePipeline(StructuredResponseDecoder<T> decoder,
                                      StructuredResponseValidator<T> validator) {
        this.decoder = Objects.requireNonNull(decoder, "decoder must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public T decodeAndValidate(String content) {
        if (content == null || content.isBlank()) {
            throw new StructuredResponseValidationException(
                    java.util.List.of("structured response content must not be blank"));
        }
        return validator.requireValid(decoder.decode(content));
    }
}
