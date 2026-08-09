package com.reportai.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reportai.application.config.properties.ReportAiLlmProperties;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.ValidatingLlmProvider;
import com.reportai.llm.prompt.PromptRenderer;
import com.reportai.llm.prompt.PromptRepository;
import com.reportai.llm.prompt.DefaultPromptRenderer;
import com.reportai.llm.http.OpenAiCompatibleLlmProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
public class LlmConfiguration {
    @Bean
    public PromptRenderer promptRenderer(PromptRepository repository) {
        return new DefaultPromptRenderer(repository);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "report-ai.llm", name = "enabled", havingValue = "true")
    public OpenAiCompatibleLlmProvider openAiCompatibleLlmProvider(ReportAiLlmProperties properties,
                                                                    ObjectMapper objectMapper) {
        return new OpenAiCompatibleLlmProvider(properties.getProvider(), URI.create(properties.getEndpoint()),
                properties.getModel(), properties.getApiKey(), properties.getConnectTimeout(),
                properties.getReadTimeout(), properties.getReasoningEffort(), objectMapper);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "report-ai.llm", name = "enabled", havingValue = "true")
    public LlmProvider validatingLlmProvider(OpenAiCompatibleLlmProvider provider) {
        return new ValidatingLlmProvider(provider);
    }
}
