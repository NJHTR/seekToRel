package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.document.model.BlockType;
import com.reportai.document.model.Document;
import com.reportai.document.model.DocumentBlock;
import com.reportai.llm.ChatAttachmentResolver;
import com.reportai.llm.LlmProvider;
import com.reportai.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPipelineExecutorTest {
    @Test
    void commitsOneAgentAtATimeAndResumesFromDurableCursor() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        Agent first = agent("first", AgentResult.Status.COMPLETED, Map.of("firstOutput", "ok"));
        Agent second = new Agent() {
            private int calls;

            @Override
            public String type() {
                return "second";
            }

            @Override
            public AgentResult execute(AgentContext context) {
                calls++;
                return new AgentResult(calls == 1 ? AgentResult.Status.WAITING : AgentResult.Status.COMPLETED,
                        Map.of("secondCalls", calls), calls == 1 ? "waiting for runtime" : "done");
            }
        };
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of("input", "x"),
                (LlmProvider) null, (ToolRegistry) null);

        AgentPipelineResult firstStep = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.RUNNING, firstStep.status());
        assertEquals(1, firstStep.checkpoint().nextAgentIndex());
        assertEquals(List.of("first"), firstStep.checkpoint().state().get("completedAgents"));

        AgentPipelineResult waiting = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.WAITING, waiting.status());
        assertEquals(1, waiting.checkpoint().nextAgentIndex());
        assertEquals("second", waiting.checkpoint().state().get("waitingAgent"));

        AgentPipelineResult completed = executor.run("pipeline-1", context, List.of(first, second));
        assertEquals(AgentPipelineStatus.COMPLETED, completed.status());
        assertEquals(2, completed.checkpoint().nextAgentIndex());
        assertEquals(3, completed.checkpoint().version());
        assertEquals(List.of("first", "second"), completed.checkpoint().state().get("completedAgents"));
        assertEquals("x", completed.checkpoint().state().get("input"));
        assertEquals("ok", completed.checkpoint().state().get("firstOutput"));
    }

    @Test
    void rejectsRuntimeAgentListThatCannotSafelyResumeCheckpoint() {
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(),
                new InMemoryAgentPipelineRepository());
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);
        executor.start("pipeline-1", context, List.of(agent("first", AgentResult.Status.COMPLETED, Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> executor.run("pipeline-1", context, List.of(agent("different", AgentResult.Status.COMPLETED,
                        Map.of()))));
    }

    @Test
    void mergesNewDerivedStateIntoTheNextDurableAgentStep() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        Agent coder = agent("coder", AgentResult.Status.COMPLETED, Map.of("code", "print(1)"));
        Agent runtime = new Agent() {
            @Override
            public String type() {
                return "runtime";
            }

            @Override
            public AgentResult execute(AgentContext context) {
                assertEquals(Map.of("source", "print(1)"), context.state().get("runtimeRequest"));
                return new AgentResult(AgentResult.Status.COMPLETED, Map.of("execution", "ok"), "done");
            }
        };
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);

        AgentContext initial = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);
        AgentPipelineResult coderStep = executor.run("pipeline-1", initial, List.of(coder, runtime));
        assertEquals(AgentPipelineStatus.RUNNING, coderStep.status());

        AgentContext next = new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("runtimeRequest", Map.of("source", "print(1)")),
                (LlmProvider) null, (ToolRegistry) null);
        AgentPipelineResult runtimeStep = executor.run("pipeline-1", next, List.of(coder, runtime));
        assertEquals(AgentPipelineStatus.COMPLETED, runtimeStep.status());
        assertEquals("ok", runtimeStep.checkpoint().state().get("execution"));
    }

    @Test
    void restoresCurrentCanonicalDocumentWhenDurableCheckpointContainsJsonMap() {
        JsonRoundTripRepository repository = new JsonRoundTripRepository();
        TenantId tenant = new TenantId("tenant-1");
        Document document = new Document("doc-1", tenant, "reports/source.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                List.of(new DocumentBlock(BlockType.TITLE, "Report", List.of(), null)), List.of(), List.of());
        Agent reviewer = new Agent() {
            private int calls;

            @Override
            public String type() {
                return "reviewer";
            }

            @Override
            public AgentResult execute(AgentContext context) {
                calls++;
                if (!(context.state().get("document") instanceof Document)) {
                    return new AgentResult(AgentResult.Status.FAILED, Map.of(), "document was not rehydrated");
                }
                return calls == 1
                        ? new AgentResult(AgentResult.Status.FAILED, Map.of(), "retry once")
                        : new AgentResult(AgentResult.Status.COMPLETED, Map.of("reviewPassed", true), "passed");
            }
        };
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", tenant, Map.of("document", document),
                (LlmProvider) null, (ToolRegistry) null, Map.of(),
                ChatAttachmentResolver.none(), Set.of("document"));

        AgentPipelineResult first = executor.run("pipeline-1", context, List.of(reviewer));
        assertEquals(AgentPipelineStatus.FAILED, first.status());

        AgentPipelineResult retry = executor.run("pipeline-1", context, List.of(reviewer));
        assertEquals(AgentPipelineStatus.COMPLETED, retry.status());
        assertEquals(Boolean.TRUE, retry.checkpoint().state().get("reviewPassed"));
    }

    @Test
    void migratesLegacyCoderRuntimeCheckpointAndPreservesManifestDigest() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        String digest = "manifest-digest-1";
        repository.create(new AgentPipelineCheckpoint("pipeline-1", "task-1", new TenantId("tenant-1"),
                List.of("coder", "runtime"), 1, AgentPipelineStatus.RUNNING,
                Map.of("environmentManifestDigest", digest, "code", "print(1)"),
                "legacy runtime pending", 0, Instant.now()));
        Agent coder = agent("coder", AgentResult.Status.COMPLETED, Map.of("code", "should-not-run"));
        Agent preflight = agent("preflight", AgentResult.Status.COMPLETED,
                Map.of("preflight", Map.of("accepted", true, "manifestDigest", digest)));
        Agent runtime = agent("runtime", AgentResult.Status.COMPLETED, Map.of("execution", "ok"));
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);

        AgentPipelineResult result = executor.run("pipeline-1", context, List.of(coder, preflight, runtime));
        assertEquals(AgentPipelineStatus.RUNNING, result.status());
        assertEquals(2, result.checkpoint().nextAgentIndex());
        assertEquals(List.of("coder", "preflight", "runtime"), result.checkpoint().agentTypes());
        assertEquals(digest, result.checkpoint().state().get("environmentManifestDigest"));
        AgentPipelineResult completed = executor.run("pipeline-1", context, List.of(coder, preflight, runtime));
        assertEquals(AgentPipelineStatus.COMPLETED, completed.status());
        assertEquals(digest, completed.checkpoint().state().get("environmentManifestDigest"));
    }

    @Test
    void rewindsExecutionFailureToInstallerAfterVolumeCleanup() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        Agent coder = agent("coder", AgentResult.Status.COMPLETED, Map.of("code", "print(1)"));
        Agent preflight = agent("preflight", AgentResult.Status.COMPLETED, Map.of());
        Agent installer = agent("installer", AgentResult.Status.COMPLETED,
                Map.of("installation", Map.of("dependencyVolume", "volume-2")));
        Agent execution = new Agent() {
            private int calls;
            @Override public String type() { return "execution"; }
            @Override public AgentResult execute(AgentContext context) {
                calls++;
                return calls == 1
                        ? new AgentResult(AgentResult.Status.FAILED,
                        Map.of("rewindToInstaller", true, "cleanupPerformed", true), "execution failed")
                        : new AgentResult(AgentResult.Status.COMPLETED, Map.of("execution", "ok"), "done");
            }
        };
        Agent cleanup = agent("cleanup", AgentResult.Status.COMPLETED,
                Map.of("cleanup", Map.of("cleaned", true)));
        List<Agent> agents = List.of(coder, preflight, installer, execution, cleanup);
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);

        executor.run("pipeline-1", context, agents);
        executor.run("pipeline-1", context, agents);
        executor.run("pipeline-1", context, agents);
        AgentPipelineResult failed = executor.run("pipeline-1", context, agents);
        assertEquals(AgentPipelineStatus.FAILED, failed.status());
        assertEquals(2, failed.checkpoint().nextAgentIndex());
        assertEquals("installer", failed.checkpoint().state().get("retryFrom"));

        AgentPipelineResult installerRetry = executor.run("pipeline-1", context, agents);
        assertEquals(AgentPipelineStatus.RUNNING, installerRetry.status());
        assertEquals(3, installerRetry.checkpoint().nextAgentIndex());
        AgentPipelineResult executionRetry = executor.run("pipeline-1", context, agents);
        assertEquals(AgentPipelineStatus.RUNNING, executionRetry.status());
        assertEquals(4, executionRetry.checkpoint().nextAgentIndex());
        AgentPipelineResult completed = executor.run("pipeline-1", context, agents);
        assertEquals(AgentPipelineStatus.COMPLETED, completed.status());
        assertEquals(5, completed.checkpoint().nextAgentIndex());
    }

    @Test
    void rewindsRuntimeFailureToCoderWithBoundedFeedback() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        Agent coder = agent("coder", AgentResult.Status.COMPLETED, Map.of("code", "print(1)"));
        Agent preflight = agent("preflight", AgentResult.Status.COMPLETED, Map.of());
        Agent installer = agent("installer", AgentResult.Status.COMPLETED, Map.of());
        Agent execution = new Agent() {
            @Override public String type() { return "execution"; }
            @Override public AgentResult execute(AgentContext context) {
                return new AgentResult(AgentResult.Status.FAILED,
                        Map.of("rewindToCoder", true, "toolErrorCode", "PYTHON_RUNTIME_ERROR",
                                "toolStderrSummary", "ValueError: invalid XML parent", "toolExitCode", 1L),
                        "runtime failed");
            }
        };
        List<Agent> agents = List.of(coder, preflight, installer, execution);
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);

        executor.run("pipeline-1", context, agents);
        executor.run("pipeline-1", context, agents);
        executor.run("pipeline-1", context, agents);
        AgentPipelineResult failed = executor.run("pipeline-1", context, agents);

        assertEquals(AgentPipelineStatus.FAILED, failed.status());
        assertEquals(0, failed.checkpoint().nextAgentIndex());
        assertEquals("coder", failed.checkpoint().state().get("retryFrom"));
        assertEquals("ValueError: invalid XML parent",
                ((Map<?, ?>) failed.checkpoint().state().get("runtimeFailureFeedback")).get("stderr"));
    }

    @Test
    void migratesIntermediateCursorToArtifactAwareLifecycle() {
        InMemoryAgentPipelineRepository repository = new InMemoryAgentPipelineRepository();
        repository.create(new AgentPipelineCheckpoint("pipeline-1", "task-1", new TenantId("tenant-1"),
                List.of("coder", "preflight", "runtime"), 2, AgentPipelineStatus.RUNNING,
                Map.of("code", "print(1)"), "runtime pending", 0, Instant.now()));
        List<Agent> agents = List.of(
                agent("coder", AgentResult.Status.COMPLETED, Map.of()),
                agent("preflight", AgentResult.Status.COMPLETED, Map.of()),
                agent("installer", AgentResult.Status.COMPLETED, Map.of()),
                agent("execution", AgentResult.Status.COMPLETED, Map.of()),
                agent("artifact-persistence", AgentResult.Status.COMPLETED, Map.of()),
                agent("cleanup", AgentResult.Status.COMPLETED, Map.of()));
        AgentPipelineExecutor executor = new AgentPipelineExecutor(new DefaultAgentOrchestrator(), repository);
        AgentContext context = new AgentContext("task-1", new TenantId("tenant-1"), Map.of(),
                (LlmProvider) null, (ToolRegistry) null);
        AgentPipelineResult result = executor.run("pipeline-1", context, agents);
        assertEquals(List.of("coder", "preflight", "installer", "execution", "artifact-persistence", "cleanup"),
                result.checkpoint().agentTypes());
        assertEquals(3, result.checkpoint().nextAgentIndex());
    }

    private static Agent agent(String type, AgentResult.Status status, Map<String, Object> outputs) {
        return new Agent() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public AgentResult execute(AgentContext context) {
                return new AgentResult(status, outputs, "result");
            }
        };
    }

    /** Simulates the JSONB round-trip performed by the PostgreSQL checkpoint repository. */
    private static final class JsonRoundTripRepository implements AgentPipelineRepository {
        private AgentPipelineCheckpoint persisted;

        @Override
        public Optional<AgentPipelineCheckpoint> findById(String pipelineId) {
            return persisted == null || !persisted.pipelineId().equals(pipelineId)
                    ? Optional.empty() : Optional.of(persisted);
        }

        @Override
        public AgentPipelineCheckpoint create(AgentPipelineCheckpoint checkpoint) {
            if (persisted != null) throw new IllegalStateException("checkpoint exists");
            persisted = encode(checkpoint);
            // PostgreSQL returns the caller's checkpoint from INSERT; the next
            // delivery reads the JSON-decoded value through findById.
            return checkpoint;
        }

        @Override
        public AgentPipelineCheckpoint update(AgentPipelineCheckpoint checkpoint, long expectedVersion) {
            if (persisted == null || persisted.version() != expectedVersion) {
                throw new IllegalStateException("version conflict");
            }
            persisted = encode(checkpoint);
            return checkpoint;
        }

        private static AgentPipelineCheckpoint encode(AgentPipelineCheckpoint checkpoint) {
            Map<String, Object> state = new java.util.LinkedHashMap<>(checkpoint.state());
            Object raw = state.get("document");
            if (raw instanceof Document document) {
                state.put("document", Map.of("id", document.id(), "tenantId", document.tenantId().value()));
            }
            return new AgentPipelineCheckpoint(checkpoint.pipelineId(), checkpoint.taskId(), checkpoint.tenantId(),
                    checkpoint.agentTypes(), checkpoint.nextAgentIndex(), checkpoint.status(), state,
                    checkpoint.message(), checkpoint.version(), checkpoint.updatedAt());
        }
    }
}
