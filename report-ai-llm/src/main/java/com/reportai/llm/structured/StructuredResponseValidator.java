package com.reportai.llm.structured;

public interface StructuredResponseValidator<T> {
    ValidationResult validate(T response);

    default T requireValid(T response) {
        ValidationResult result = validate(response);
        if (result == null || !result.valid()) {
            throw new StructuredResponseValidationException(
                    result == null ? java.util.List.of("validator returned no result") : result.violations());
        }
        return response;
    }
}
