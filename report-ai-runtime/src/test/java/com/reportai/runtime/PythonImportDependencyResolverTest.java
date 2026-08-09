package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonImportDependencyResolverTest {
    @Test
    void mapsScientificImportsAndIgnoresStandardLibrary() {
        String source = """
                import os
                import numpy as np
                from PIL import Image
                from matplotlib import pyplot as plt
                import torch
                from torchvision.utils import make_grid
                import yaml
                """;

        assertEquals(List.of("numpy", "pillow", "matplotlib", "torch", "torchvision", "pyyaml"),
                new PythonImportDependencyResolver().resolvePackageNames(source));
    }

    @Test
    void deduplicatesImportsAndDoesNotGuessUnknownPackages() {
        String source = "import torch, numpy as np\nimport torch.nn as nn\nimport user_owned_package\n";

        assertEquals(List.of("torch", "numpy"), new PythonImportDependencyResolver().resolvePackageNames(source));
    }
}
