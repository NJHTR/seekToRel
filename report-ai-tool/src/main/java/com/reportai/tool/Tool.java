package com.reportai.tool;

public interface Tool {
    String name();

    ToolResult execute(ToolContext context, ToolInput input);
}
