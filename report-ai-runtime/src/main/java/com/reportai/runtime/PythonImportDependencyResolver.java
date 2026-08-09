package com.reportai.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministically translates Python imports into package-name intent.
 *
 * <p>The model may generate source, but it never chooses an image or a wheel
 * hash.  This resolver only recognizes the first-party scientific profile's
 * module-to-distribution mapping.  Exact versions and transitive dependencies
 * are selected later by {@link EnvironmentResolver} from the signed catalog.</p>
 */
public final class PythonImportDependencyResolver {
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*(from|import)\\s+([^#\\r\\n]+)");
    private static final Map<String, String> MODULE_TO_DISTRIBUTION = Map.ofEntries(
            Map.entry("numpy", "numpy"),
            Map.entry("PIL", "pillow"),
            Map.entry("matplotlib", "matplotlib"),
            Map.entry("torch", "torch"),
            Map.entry("torchvision", "torchvision"),
            Map.entry("yaml", "pyyaml"),
            Map.entry("sympy", "sympy"),
            Map.entry("networkx", "networkx"),
            Map.entry("jinja2", "jinja2"),
            Map.entry("dateutil", "python-dateutil"),
            Map.entry("packaging", "packaging"),
            Map.entry("cv2", "opencv-python"));
    private static final Set<String> STANDARD_LIBRARY_ROOTS = Set.of(
            "abc", "argparse", "array", "asyncio", "base64", "collections", "concurrent",
            "contextlib", "copy", "csv", "dataclasses", "datetime", "decimal", "enum",
            "functools", "glob", "gzip", "hashlib", "io", "itertools", "json", "logging",
            "math", "os", "pathlib", "pickle", "random", "re", "shutil", "statistics",
            "string", "subprocess", "sys", "tempfile", "textwrap", "threading", "time",
            "traceback", "typing", "unittest", "uuid", "warnings", "zipfile");

    public List<String> resolvePackageNames(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT.matcher(source);
        while (importMatcher.find()) {
            String keyword = importMatcher.group(1);
            String value = importMatcher.group(2);
            if ("from".equals(keyword)) {
                String module = value.trim().split("\\s+import\\s+", 2)[0].trim();
                addModule(result, module);
                continue;
            }
            for (String item : value.split(",")) {
                String module = item.trim().split("\\s+as\\s+", 2)[0].trim();
                if (!module.isBlank()) addModule(result, module);
            }
        }
        return List.copyOf(result);
    }

    private static void addModule(Set<String> result, String module) {
        String root = module.substring(0, module.indexOf('.') >= 0 ? module.indexOf('.') : module.length());
        String distribution = MODULE_TO_DISTRIBUTION.get(root);
        if (distribution != null) {
            result.add(distribution);
        } else if (!STANDARD_LIBRARY_ROOTS.contains(root)) {
            // Unknown imports are deliberately not guessed.  The runtime will
            // surface ModuleNotFoundError rather than installing an unapproved
            // package from an arbitrary source.
        }
    }
}
