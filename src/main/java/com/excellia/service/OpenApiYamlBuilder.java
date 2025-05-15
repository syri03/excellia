package com.excellia.service;

import org.springframework.stereotype.Service;
import com.excellia.dto.ApiConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class OpenApiYamlBuilder {

    public String buildYaml(ApiConfig config) {
        StringBuilder yaml = new StringBuilder();

        String fullUrl = config.getUrl();
        if (fullUrl == null || !fullUrl.matches("^(https?)://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")) {
            throw new IllegalArgumentException("Invalid URL: " + fullUrl + ". Must be a fully qualified URL (e.g., https://example.com/path).");
        }

        String operationPath = "/";

        yaml.append("openapi: 3.1.0\n")
            .append("info:\n")
            .append("  title: Generated API\n")
            .append("  version: 1.0.0\n")
            .append("servers:\n")
            .append("  - url: ").append(fullUrl).append("\n")
            .append("paths:\n")
            .append("  ").append(operationPath).append(":\n");

        List<String> methodsToGenerate = config.getMethods() != null && !config.getMethods().isEmpty() 
            ? config.getMethods() 
            : List.of(config.getMethod() != null ? config.getMethod().toLowerCase() : "get");

        for (String httpMethod : methodsToGenerate) {
            httpMethod = httpMethod.toLowerCase();
            String methodOperationId = config.getOperationId() + "_" + httpMethod.toUpperCase();
            
            yaml.append("    ").append(httpMethod).append(":\n")
                .append("      operationId: ").append(methodOperationId).append("\n")
                .append("      summary: Auto-generated endpoint for ").append(methodOperationId).append("\n");

            if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
                yaml.append("      parameters:\n");
                config.getQueryParams().forEach((name, value) -> {
                    yaml.append("        - in: query\n")
                        .append("          name: ").append(name).append("\n")
                        .append("          schema:\n")
                        .append("            type: string\n");
                });
            }

            if (config.getBodies() != null && config.getBodies().containsKey(httpMethod) && 
                Arrays.asList("post", "put", "patch").contains(httpMethod)) {
                yaml.append("      requestBody:\n")
                    .append("        content:\n")
                    .append("          application/json:\n")
                    .append("            schema:\n")
                    .append("              type: object\n")
                    .append("              properties:\n");
                Map<String, Object> body = (Map<String, Object>) config.getBodies().get(httpMethod);
                body.forEach((key, value) -> {
                    yaml.append("                ").append(key).append(":\n")
                        .append("                  type: ").append(inferType(value)).append("\n");
                });
            } else if (config.getBody() != null && !config.getBody().toString().isEmpty() && 
                Arrays.asList("post", "put", "patch").contains(httpMethod)) {
                yaml.append("      requestBody:\n")
                    .append("        content:\n")
                    .append("          application/json:\n")
                    .append("            schema:\n")
                    .append("              type: object\n");
            }

            yaml.append("      responses:\n")
                .append("        '200':\n")
                .append("          description: Success\n")
                .append("          content:\n")
                .append("            application/json:\n")
                .append("              schema:\n")
                .append("                type: object\n");
        }

        return yaml.toString();
    }

    private String inferType(Object value) {
        if (value instanceof String) {
            return "string";
        } else if (value instanceof Number) {
            return "number";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof Map || value instanceof List) {
            return "object";
        } else {
            return "string"; // Default fallback
        }
    }
}