package com.reportai.runtime;

/** Bounded result returned by a sandbox client. */
public record SandboxExecution(int exitCode, boolean timedOut, boolean outputLimitExceeded,
                               String stdout, String stderr) {
    public SandboxExecution {
        stdout = stdout == null ? "" : stdout;
        stderr = stderr == null ? "" : stderr;
    }
}
