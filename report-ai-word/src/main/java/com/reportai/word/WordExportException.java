package com.reportai.word;

public class WordExportException extends RuntimeException {
    public WordExportException(String message) {
        super(message);
    }

    public WordExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
