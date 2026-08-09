package com.reportai.runtime;

import java.util.List;

/** Creates the only supported in-sandbox dependency installation command. */
final class PythonDependencyCommand {
    private static final String WHEELHOUSE = "/opt/reportai/wheels";
    private static final String TARGET = "/tmp/reportai-deps";
    /** Named-volume target; /home/reportai is owned by the non-root runtime user in the image. */
    static final String DEPENDENCY_VOLUME_TARGET = "/home/reportai";
    static final String DEPENDENCY_TARGET = "/home/reportai/.reportai-deps";
    /** Kept as an alias for callers/tests that describe the Python path rather than its mount. */
    static final String WORKSPACE_TARGET = DEPENDENCY_TARGET;
    static final String WORKSPACE_REQUIREMENTS = "/workspace/.reportai-requirements.txt";

    private PythonDependencyCommand() {
    }

    static List<String> build(String entryPoint, PythonDependencyRequest dependencies) {
        if (!dependencies.resolvedDependencies().isEmpty()) {
            return buildLocked(entryPoint, dependencies.resolvedDependencies());
        }
        StringBuilder install = new StringBuilder()
                .append("python -m pip install --isolated --disable-pip-version-check --no-index ")
                .append("--find-links=").append(WHEELHOUSE).append(" --target ").append(TARGET);
        dependencies.packages().forEach(value -> install.append(' ').append(shellQuote(value)));
        String command = "set -eu; trap 'rm -rf " + TARGET + "' EXIT INT TERM; "
                + install + "; PYTHONPATH=" + TARGET + " python " + shellQuote(entryPoint);
        return List.of("sh", "-c", command);
    }

    /** Builds the installer-only command used by the two-container lifecycle. */
    static List<String> buildInstaller(PythonDependencyRequest dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            throw new IllegalArgumentException("installer requires dependencies");
        }
        String install;
        String prefix = "python -m pip install --isolated --disable-pip-version-check --no-index "
                + "--find-links=" + WHEELHOUSE + " --target " + TARGET;
        if (dependencies.resolvedDependencies().isEmpty()) {
            StringBuilder command = new StringBuilder(prefix);
            dependencies.packages().forEach(value -> command.append(' ').append(shellQuote(value)));
            install = command.toString();
        } else {
            StringBuilder requirements = new StringBuilder();
            for (DependencySpec dependency : dependencies.resolvedDependencies()) {
                if (dependency.sha256() == null) {
                    throw new IllegalArgumentException("resolved dependencies require sha256 hashes");
                }
                requirements.append(dependency.name()).append("==").append(dependency.version())
                        .append(" --hash=sha256:").append(dependency.sha256()).append('\n');
            }
            install = "cat > " + WORKSPACE_REQUIREMENTS + " <<'REPORTAI_REQUIREMENTS'\n"
                    + requirements + "REPORTAI_REQUIREMENTS\n"
                    + prefix + " --require-hashes -r " + WORKSPACE_REQUIREMENTS;
        }
        String stage = "rm -rf " + TARGET + "; mkdir -p " + TARGET + "; "
                + install + "; rm -rf " + DEPENDENCY_TARGET + "; mkdir -p " + DEPENDENCY_TARGET
                + "; cp -a " + TARGET + "/. " + DEPENDENCY_TARGET + "; rm -rf " + TARGET;
        return List.of("sh", "-c", "set -eu; " + stage);
    }

    /** Builds the execution-only command after the installer container exits. */
    static List<String> buildExecution(String entryPoint) {
        return List.of("sh", "-c", "set -eu; PYTHONPATH=" + shellQuote(DEPENDENCY_TARGET)
                + " python " + shellQuote(entryPoint));
    }

    private static List<String> buildLocked(String entryPoint, List<DependencySpec> dependencies) {
        StringBuilder requirements = new StringBuilder();
        for (DependencySpec dependency : dependencies) {
            if (dependency.sha256() == null) {
                throw new IllegalArgumentException("resolved dependencies require sha256 hashes");
            }
            requirements.append(dependency.name()).append("==").append(dependency.version())
                    .append(" --hash=sha256:").append(dependency.sha256()).append('\n');
        }
        String install = "python -m pip install --isolated --disable-pip-version-check --no-index "
                + "--require-hashes --find-links=" + WHEELHOUSE + " --target " + TARGET
                + " -r /tmp/reportai-requirements.txt";
        String command = "set -eu; trap 'rm -rf " + TARGET + " /tmp/reportai-requirements.txt' EXIT INT TERM; "
                + "cat > /tmp/reportai-requirements.txt <<'REPORTAI_REQUIREMENTS'\n"
                + requirements + "REPORTAI_REQUIREMENTS\n"
                + install + "; PYTHONPATH=" + TARGET + " python " + shellQuote(entryPoint);
        return List.of("sh", "-c", command);
    }

    private static String shellQuote(String value) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9_.:/=-]+")) {
            throw new IllegalArgumentException("unsafe dependency command token");
        }
        return "'" + value + "'";
    }
}
