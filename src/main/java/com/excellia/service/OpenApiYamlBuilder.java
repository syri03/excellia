package com.excellia.service;

import com.excellia.dto.ApiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.util.*;

@Component
public class OpenApiYamlBuilder {
    private static final Logger log = LoggerFactory.getLogger(OpenApiYamlBuilder.class);

    public String buildYaml(ApiConfig config) {
        log.info("Building OpenAPI YAML from config");

        Map<String, Object> openApiSpec = new LinkedHashMap<>();
        openApiSpec.put("openapi", "3.1.0");
        openApiSpec.put("info", Map.of(
            "title", "Generated API",
            "version", "1.0.0"
        ));
        openApiSpec.put("servers", List.of(
            Map.of("url", config.getUrl())
        ));

        Map<String, Object> paths = new LinkedHashMap<>();
        Map<String, Object> pathItem = new LinkedHashMap<>();

        List<String> methods = config.getMethods() != null ? config.getMethods() : List.of(config.getMethod().toLowerCase());
        for (String method : methods) {
            String httpMethod = method.toLowerCase();
            Map<String, Object> operation = new LinkedHashMap<>();
            operation.put("operationId", config.getOperationId() + "_" + httpMethod.toUpperCase());
            operation.put("responses", Map.of(
                "200", Map.of(
                    "description", "Successful response",
                    "content", Map.of(
                        "application/json", Map.of(
                            "schema", Map.of("type", "object")
                        )
                    )
                )
            ));

            if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
                List<Map<String, Object>> parameters = new ArrayList<>();
                config.getQueryParams().forEach((name, value) -> {
                    parameters.add(Map.of(
                        "name", name,
                        "in", "query",
                        "schema", Map.of("type", "string")
                    ));
                });
                operation.put("parameters", parameters);
            }

            if (List.of("post", "put", "patch").contains(httpMethod)) {
                Object requestBody = null;
                if (config.getBodies() != null && config.getBodies().containsKey(httpMethod)) {
                    requestBody = config.getBodies().get(httpMethod);
                    log.debug("Using method-specific body for {}: {}", httpMethod, requestBody);
                } else if (config.getBody() != null) {
                    requestBody = config.getBody();
                    log.debug("Using fallback body for {}: {}", httpMethod, requestBody);
                }

                if (requestBody != null) {
                    operation.put("requestBody", Map.of(
                        "content", Map.of(
                            "application/json", Map.of(
                                "schema", generateSchema(requestBody)
                            )
                        )
                    ));
                }
            }

            pathItem.put(httpMethod, operation);
        }

        paths.put("/", pathItem);
        openApiSpec.put("paths", paths);

        // Return YAML as string
        return new Yaml().dump(openApiSpec);
    }

    private Map<String, Object> generateSchema(Object data) {
        Map<String, Object> schema = new LinkedHashMap<>();
        if (data instanceof Map) {
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            ((Map<?, ?>) data).forEach((key, value) -> {
                properties.put(key.toString(), Map.of("type", getType(value)));
            });
            schema.put("properties", properties);
        } else {
            schema.put("type", getType(data));
        }
        return schema;
    }

    private String getType(Object value) {
        if (value instanceof String) return "string";
        if (value instanceof Integer || value instanceof Long) return "integer";
        if (value instanceof Double || value instanceof Float) return "number";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof List) return "array";
        return "object";
    }
}