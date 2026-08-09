package com.reportai.llm;

import java.util.List;
import java.util.Map;

/**
 * Resolves bounded, tenant-authorized attachments immediately before an LLM
 * request. Implementations must not put binary data in durable agent state.
 */
@FunctionalInterface
public interface ChatAttachmentResolver {
    List<ChatContentPart> resolve(String agentType, Map<String, Object> state);

    static ChatAttachmentResolver none() {
        return (agentType, state) -> List.of();
    }
}
