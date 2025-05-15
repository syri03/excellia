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

    public void generateCode() {
        generateCodeInternal(Paths.get(YAML_DIR, YAML_FILE).toString());
    }

    public void generateCodeFromConfig(ApiConfig apiConfig) {
        String yamlPath = Paths.get(YAML_DIR, YAML_FILE).toString();
        generateCodeInternal(yamlPath);
    }

    private void generateCodeInternal(String yamlAbsolutePath) {
        try {
            cleanOutputDirectory();

            CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName("java")
                .setLibrary("resttemplate")
                .setInputSpec(new File(yamlAbsolutePath).getAbsolutePath())
                .setOutputDir(OUTPUT_DIR)
                .setApiPackage(BASE_PACKAGE + ".api")
                .setModelPackage(BASE_PACKAGE + ".model")
                .setInvokerPackage(BASE_PACKAGE + ".core")
                .setValidateSpec(true);

            Map<String, Object> props = new HashMap<>();
            props.put("useSpringBoot", "true");
            props.put("dateLibrary", "java8");
            props.put("serializationLibrary", "jackson");
            props.put("openApiNullable", "false");
            props.put("useServerUrl", "true"); // Ensure generated client uses server URL from YAML
            configurator.setAdditionalProperties(props);

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

    private void cleanOutputDirectory() {
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (Files.exists(outputPath)) {
            deleteDirectory(outputPath.toFile());
        }
    }

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