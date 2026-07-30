package com.reportai.application.config;

import com.reportai.common.id.IdGenerator;
import com.reportai.common.id.UuidIdGenerator;
import com.reportai.common.idempotency.IdempotencyStore;
import com.reportai.storage.postgres.PostgresIdempotencyStore;
import com.reportai.storage.postgres.PostgresDocumentRepository;
import com.reportai.storage.postgres.PostgresReportTaskRepository;
import com.reportai.storage.postgres.PostgresWorkflowInstanceRepository;
import com.reportai.storage.postgres.PostgresWorkflowDefinitionRepository;
import com.reportai.storage.postgres.PostgresSchedulerTaskStore;
import com.reportai.storage.postgres.PostgresWorkflowEventRepository;
import com.reportai.storage.postgres.PostgresObjectIntegrityRepository;
import com.reportai.storage.postgres.PostgresSchedulerOutboxRepository;
import com.reportai.storage.postgres.PostgresPromptRepository;
import com.reportai.storage.postgres.PostgresConversationRepository;
import com.reportai.storage.postgres.PostgresConversationMemorySnapshotRepository;
import com.reportai.storage.postgres.PostgresConversationCompactionCandidateRepository;
import com.reportai.storage.ObjectIntegrityReconciliationRepository;
import com.reportai.scheduler.SchedulerTaskStore;
import com.reportai.scheduler.SchedulerOutboxRepository;
import com.reportai.workflow.WorkflowEventRepository;
import com.reportai.storage.DocumentRepository;
import com.reportai.storage.ObjectIntegrityRepository;
import com.reportai.llm.prompt.PromptRepository;
import com.reportai.agent.ConversationRepository;
import com.reportai.task.ReportTaskRepository;
import com.reportai.task.TaskApplicationService;
import com.reportai.workflow.WorkflowInstanceRepository;
import com.reportai.workflow.WorkflowDefinitionRepository;
import com.reportai.workflow.DefaultWorkflowEngine;
import com.reportai.workflow.WorkflowEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class PersistenceConfiguration {
    @Bean
    public IdGenerator idGenerator() {
        return new UuidIdGenerator();
    }

    @Bean
    public ReportTaskRepository reportTaskRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresReportTaskRepository(jdbcTemplate);
    }

    @Bean
    public DocumentRepository documentRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PostgresDocumentRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public PostgresObjectIntegrityRepository objectIntegrityRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresObjectIntegrityRepository(jdbcTemplate);
    }

    @Bean
    public ObjectIntegrityReconciliationRepository objectIntegrityReconciliationRepository(
            PostgresObjectIntegrityRepository repository) {
        return repository;
    }

    @Bean
    public IdempotencyStore idempotencyStore(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresIdempotencyStore(jdbcTemplate);
    }

    @Bean
    public WorkflowInstanceRepository workflowInstanceRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                                 com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PostgresWorkflowInstanceRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public WorkflowDefinitionRepository workflowDefinitionRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                                      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PostgresWorkflowDefinitionRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public SchedulerTaskStore schedulerTaskStore(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresSchedulerTaskStore(jdbcTemplate);
    }

    @Bean
    public SchedulerOutboxRepository schedulerOutboxRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresSchedulerOutboxRepository(jdbcTemplate);
    }

    @Bean
    public PromptRepository promptRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresPromptRepository(jdbcTemplate);
    }

    @Bean
    public ConversationRepository conversationRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PostgresConversationRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public com.reportai.agent.ConversationMemorySnapshotRepository conversationMemorySnapshotRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresConversationMemorySnapshotRepository(jdbcTemplate);
    }

    @Bean
    public com.reportai.agent.ConversationCompactionCandidateRepository conversationCompactionCandidateRepository(
            NamedParameterJdbcTemplate jdbcTemplate) {
        return new PostgresConversationCompactionCandidateRepository(jdbcTemplate);
    }

    @Bean
    public WorkflowEventRepository workflowEventRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new PostgresWorkflowEventRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    public WorkflowEngine workflowEngine(WorkflowDefinitionRepository definitions,
                                         WorkflowInstanceRepository instances,
                                         IdGenerator idGenerator,
                                         WorkflowEventRepository events) {
        return new DefaultWorkflowEngine(definitions, instances, idGenerator,
                new com.reportai.workflow.WorkflowStateMachine(), events);
    }

    @Bean
    public TaskApplicationService taskApplicationService(ReportTaskRepository taskRepository,
                                                         IdempotencyStore idempotencyStore,
                                                         IdGenerator idGenerator) {
        return new TaskApplicationService(taskRepository, idempotencyStore, idGenerator);
    }
}
