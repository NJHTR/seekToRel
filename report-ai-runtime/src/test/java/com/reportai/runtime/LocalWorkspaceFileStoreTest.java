package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalWorkspaceFileStoreTest {
    @Test
    void writesOnlyInsideValidatedWorkspace() throws Exception {
        Path root = Files.createTempDirectory("reportai-workspace-");
        LocalWorkspaceFileStore store = new LocalWorkspaceFileStore(root);

        store.writeUtf8("task-1", "src/main.py", "print('ok')");

        assertEquals("print('ok')", Files.readString(root.resolve("task-1/src/main.py")));
        assertThrows(IllegalArgumentException.class,
                () -> store.writeUtf8("task-1", "../escape.py", "print(1)"));
        assertThrows(IllegalArgumentException.class,
                () -> store.writeUtf8("task-1", "main.py", " "));
    }
}
