package com.excellia.controller;

import com.excellia.dto.ApiConfig;
import com.excellia.service.DynamicApiCallerService;
import com.excellia.service.OpenApiCodeGenLibraryService;
import com.excellia.service.OpenApiGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/openapi")
public class OpenApiGeneratorController {
    private static final Logger log = LoggerFactory.getLogger(OpenApiGeneratorController.class);
    private final OpenApiGeneratorService openApiGeneratorService;
    private final OpenApiCodeGenLibraryService codeGenLibraryService;
    private final DynamicApiCallerService dynamicApiCallerService;
    private final ObjectMapper objectMapper;

    public OpenApiGeneratorController(
            OpenApiGeneratorService openApiGeneratorService,
            OpenApiCodeGenLibraryService codeGenLibraryService,
            DynamicApiCallerService dynamicApiCallerService,
            ObjectMapper objectMapper) {
        this.openApiGeneratorService = openApiGeneratorService;
        this.codeGenLibraryService = codeGenLibraryService;
        this.dynamicApiCallerService = dynamicApiCallerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/generate")
    public ResponseEntity<String> generateFromConfig(@RequestBody ApiConfig config) {
        try {
            String configJson = objectMapper.writeValueAsString(config);
            log.info("Generating OpenAPI spec and client code for config: {}", configJson);

            if (config.getUrl() == null || config.getUrl().isEmpty()) {
                log.error("URL is required for generation");
                return ResponseEntity.status(400).body("❌ URL is required for generation");
            }

            openApiGeneratorService.generateFromConfig(config);
            codeGenLibraryService.generateCode();
            
            log.info("Generation complete for operation: {}", config.getOperationId());
            return ResponseEntity.ok("✅ Generation complete");
        } catch (Exception e) {
            log.error("Error during generation: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body("❌ Error: " + e.getMessage());
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeApiCall(@RequestBody ApiConfig config) throws Exception {
        try {
            String configJson = objectMapper.writeValueAsString(config);
            log.info("Executing API call for config: {}", configJson);

            if (config.getOperationId() == null || config.getOperationId().isEmpty()) {
                log.error("OperationId is required for execution");
                return ResponseEntity.status(400).body("❌ OperationId is required");
            }

            dynamicApiCallerService.initializeGeneratedClient();
            log.debug("Generated client initialized");

            String httpMethod = config.getMethod() != null ? config.getMethod().toUpperCase() : "GET";
            String effectiveOperationId = config.getOperationId() + "_" + httpMethod;
            config.setOperationId(effectiveOperationId);
            log.debug("Adjusted operationId to: {}", effectiveOperationId);

            Object response = dynamicApiCallerService.callApi(config);
            log.info("API call successful for operation: {}", effectiveOperationId);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            log.warn("Invalid state: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body("⚠️ " + e.getMessage() + ". Call /generate first and ensure client code is generated.");
        } catch (RuntimeException e) {
            log.error("API execution failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body("❌ API Error: " + e.getMessage());
        }
    }
}