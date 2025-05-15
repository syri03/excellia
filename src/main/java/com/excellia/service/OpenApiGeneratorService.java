package com.excellia.service;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.excellia.dto.ApiConfig;

@Service
public class OpenApiGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiGeneratorService.class);
    private static final String YAML_OUTPUT_PATH = "src/main/resources/generated/openapi.yaml";
    private final OpenApiYamlBuilder yamlBuilder;

    // Constructor injection
    public OpenApiGeneratorService(OpenApiYamlBuilder yamlBuilder) {
        this.yamlBuilder = yamlBuilder;
    }

    public void generateFromConfig(ApiConfig config) throws IOException {
        // Validate inputs
        if (config == null) {
            log.error("ApiConfig is null");
            throw new IllegalArgumentException("ApiConfig cannot be null");
        }
        if (yamlBuilder == null) {
            log.error("YAML Builder is not initialized");
            throw new IllegalStateException("YAML Builder is not initialized");
        }

        // Generate YAML
        String yaml;
        try {
            yaml = yamlBuilder.buildYaml(config);
            log.info("Generated YAML:\n{}", yaml);
        } catch (IllegalArgumentException e) {
            log.error("Failed to generate YAML: {}", e.getMessage());
            throw e;
        }

        // Save YAML to file
        Path yamlPath = Paths.get(YAML_OUTPUT_PATH);
        try {
            Files.createDirectories(yamlPath.getParent());
            try (FileWriter writer = new FileWriter(yamlPath.toFile())) {
                writer.write(yaml);
                log.info("YAML saved at: {}", yamlPath);
            }
        } catch (IOException e) {
            log.error("Failed to save YAML at {}: {}", yamlPath, e.getMessage());
            throw new IOException("Failed to save YAML: " + e.getMessage(), e);
        }
    }

    public String getYamlOutputPath() {
        return YAML_OUTPUT_PATH;
    }
}