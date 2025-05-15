package com.excellia.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.springframework.stereotype.Service;

import com.excellia.dto.ApiConfig;

@Service
public class OpenApiCodeGenLibraryService {

    private static final String YAML_DIR = "src/main/resources/generated/";
    private static final String YAML_FILE = "openapi.yaml";
    private static final String OUTPUT_DIR = "target/generated-sources/openapi";
    private static final String BASE_PACKAGE = "com.excellia";

    /**
     * This method is used to generate code from a previously generated YAML file.
     * (Classic use case)
     */
    public void generateCode() {
        generateCodeInternal(Paths.get(YAML_DIR, YAML_FILE).toString());
    }

    /**
     * New method: generate code from a user-sent YAML file.
     * (if you want to generate dynamically in future versions)
     */
    public void generateCodeFromConfig(ApiConfig apiConfig) {
        // (Optional) You could generate a custom YAML per user operationId if needed
        String yamlPath = Paths.get(YAML_DIR, YAML_FILE).toString();
        generateCodeInternal(yamlPath);
    }

    /**
     * Internal reusable method to trigger OpenAPI code generation.
     */
    private void generateCodeInternal(String yamlAbsolutePath) {
        try {
            // Clean old files
            cleanOutputDirectory();

            // Configure the generator
            CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary("resttemplate")
                .setInputSpec(new File(yamlAbsolutePath).getAbsolutePath())
                .setOutputDir(OUTPUT_DIR)
                .setApiPackage(BASE_PACKAGE + ".api")
                .setModelPackage(BASE_PACKAGE + ".model")
                .setInvokerPackage(BASE_PACKAGE + ".core")
                .setValidateSpec(true);

            // Additional generation properties
            Map<String, Object> props = new HashMap<>();
            props.put("useSpringBoot", "true");
            props.put("dateLibrary", "java8");
            props.put("serializationLibrary", "jackson");
            props.put("openApiNullable", "false");
            configurator.setAdditionalProperties(props);

            // Launch the code generator
            new DefaultGenerator()
                .opts(configurator.toClientOptInput())
                .generate();

            System.out.println("✅ Successfully generated client code into:");
            System.out.println("   - " + BASE_PACKAGE + ".api");
            System.out.println("   - " + BASE_PACKAGE + ".model");
            System.out.println("   - " + BASE_PACKAGE + ".core");

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to generate API client: " + e.getMessage(), e);
        }
    }

    /**
     * Clean the output directory before new generation.
     */
    private void cleanOutputDirectory() {
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (Files.exists(outputPath)) {
            deleteDirectory(outputPath.toFile());
        }
    }

    /**
     * Recursive delete for a folder and its contents.
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
            directory.delete();
        }
    }
}