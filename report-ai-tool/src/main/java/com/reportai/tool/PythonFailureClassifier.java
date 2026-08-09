package com.reportai.tool;

import com.reportai.runtime.ExecutionResult;

import java.util.Locale;

/**
 * Maps sandbox outcomes to stable, provider-independent error codes.
 *
 * <p>The generated program is untrusted output, so the classifier never
 * parses a stack trace as a domain object. It only recognizes bounded,
 * explicit markers and common interpreter diagnostics. Keeping this policy
 * in one place prevents the legacy {@code python.run} tool and the
 * checkpoint-aware lifecycle tool from disagreeing about retryability.</p>
 */
final class PythonFailureClassifier {
    private PythonFailureClassifier() {
    }

    static String classify(ExecutionResult result) {
        if (result == null || result.status() == ExecutionResult.Status.SUCCEEDED) {
            return null;
        }
        String output = output(result);
        String normalized = output.toLowerCase(Locale.ROOT);
        if (normalized.contains("modulenotfounderror") || normalized.contains("no module named")) {
            return "PYTHON_DEPENDENCY_MISSING";
        }
        if (normalized.contains("filenotfounderror")
                || normalized.contains("no image files")
                || normalized.contains("data/gan")
                || normalized.contains("data/cycle")) {
            return "PYTHON_INPUT_MISSING";
        }
        if (isPrerequisiteFailure(output)) {
            return "PYTHON_INPUT_MISSING";
        }
        if (normalized.contains("usage:") || normalized.contains("required: ")) {
            return "PYTHON_ARGUMENT_ERROR";
        }
        if (result.status() == ExecutionResult.Status.REJECTED) {
            return "PYTHON_REQUEST_REJECTED";
        }
        return "PYTHON_RUNTIME_ERROR";
    }

    static boolean isPrerequisiteFailure(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("prerequisite failure")
                || normalized.contains("prerequisite_failure")
                || normalized.contains("missing prerequisite")
                || normalized.contains("required input is missing")
                || normalized.contains("prerequisitefailure")
                || output.contains("前置条件失败")
                || output.contains("前置条件不满足")
                || output.contains("输入缺失")
                || output.contains("人工核验")
                || output.contains("不具备 OCR")
                || output.contains("不具备OCR");
    }

    private static String output(ExecutionResult result) {
        String stdout = result.stdout() == null ? "" : result.stdout();
        String stderr = result.stderr() == null ? "" : result.stderr();
        if (stdout.isBlank()) {
            return stderr;
        }
        if (stderr.isBlank()) {
            return stdout;
        }
        return stdout + "\n" + stderr;
    }
}
