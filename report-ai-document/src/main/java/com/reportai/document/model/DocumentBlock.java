package com.reportai.document.model;

import java.util.List;

public record DocumentBlock(BlockType type, String text, List<String> children, String assetKey) {
    public DocumentBlock {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
