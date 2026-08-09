package com.reportai.parser;

/** Stable application port; adapters can later support PDF, Markdown and HTML. */
public interface DocumentParserPort {
    boolean supports(String mediaType, String fileName);

    ParserResult parse(ParserRequest request);
}
