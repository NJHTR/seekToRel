package com.reportai.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.agent.AgentPipelineExecutor;
import com.reportai.agent.AgentPipelineRepository;
import com.reportai.agent.AgentOrchestrator;
import com.reportai.agent.ConversationContextOrchestrator;
import com.reportai.agent.ConversationRepository;
import com.reportai.agent.ConversationCompactionCandidateRepository;
import com.reportai.agent.ConversationMemoryCompactor;
import com.reportai.agent.ConversationMemorySnapshotRepository;
import com.reportai.agent.ConversationMemorySnapshotService;
import com.reportai.application.agent.ConversationMemoryCompactionScheduler;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import com.reportai.agent.DeterministicConversationMemoryCompactor;
import com.reportai.agent.LlmConversationMemoryCompactor;
import com.reportai.llm.LlmProvider;
import com.reportai.storage.postgres.PostgresAgentPipelineRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Production persistence adapter for resumable agent cursors. */
@Configuration
public class AgentPipelinePersistenceConfiguration {
    @Bean
    public AgentPipelineRepository agentPipelineRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                           ObjectMapper objectMapper) {
        return new PostgresAgentPipelineRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public AgentPipelineExecutor agentPipelineExecutor(AgentOrchestrator orchestrator,
                                                       AgentPipelineRepository repository) {
        return new AgentPipelineExecutor(orchestrator, repository);
    }

    @Bean
    public ConversationMemoryCompactor deterministicConversationMemoryCompactor() {
        return new DeterministicConversationMemoryCompactor();
    }

    @Bean
    @Primary
    @ConditionalOnBean(LlmProvider.class)
    public ConversationMemoryCompactor llmConversationMemoryCompactor(LlmProvider provider) {
        return new LlmConversationMemoryCompactor(provider);
    }

    @Bean
    public ConversationContextOrchestrator conversationContextOrchestrator(
            ConversationRepository repository, ConversationMemoryCompactor compactor,
            ConversationMemorySnapshotRepository snapshots) {
        return new ConversationContextOrchestrator(repository, compactor,
                com.reportai.llm.TokenEstimator::approximate, snapshots);
    }

    @Bean
    public ConversationMemorySnapshotService conversationMemorySnapshotService(
            ConversationRepository conversations, ConversationMemorySnapshotRepository snapshots,
            ConversationMemoryCompactor compactor) {
        return new ConversationMemorySnapshotService(conversations, snapshots, compactor);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.memory-snapshot", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ConversationMemoryCompactionScheduler conversationMemoryCompactionScheduler(
            ConversationCompactionCandidateRepository candidates,
            ConversationMemorySnapshotService snapshots,
            ReportAiMemorySnapshotProperties properties,
            MeterRegistry meterRegistry) {
        return new ConversationMemoryCompactionScheduler(candidates, snapshots, properties, meterRegistry);
    }
}
