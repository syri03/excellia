package com.excellia.service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.excellia.dto.ApiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DynamicApiCallerService {
    private static final Logger log = LoggerFactory.getLogger(DynamicApiCallerService.class);
    private Object apiClient;
    private Object defaultApi;
    private boolean isGenerated = false;
    private final ObjectMapper objectMapper;

    public DynamicApiCallerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void initializeGeneratedClient() throws Exception {
        log.info("Initializing generated API client");
        try {
            Class<?> apiClientClass = Class.forName("com.excellia.core.ApiClient");
            this.apiClient = apiClientClass.getConstructor().newInstance();

            Class<?> defaultApiClass = Class.forName("com.excellia.api.DefaultApi");
            this.defaultApi = defaultApiClass.getConstructor(apiClientClass).newInstance(this.apiClient);

            this.isGenerated = true;
            log.info("API client initialized successfully");
        } catch (ClassNotFoundException e) {
            log.error("Generated API classes not found. Ensure /generate endpoint is called first: {}", e.getMessage());
            throw new IllegalStateException("Generated API classes not found. Call /generate first.", e);
        } catch (Exception e) {
            log.error("Failed to initialize API client: {}", e.getMessage());
            throw new Exception("Failed to initialize API client: " + e.getMessage(), e);
        }
    }

    public Object callApi(ApiConfig config) {
        if (!isGenerated || defaultApi == null) {
            log.error("API client not generated or initialized. Call /generate first.");
            throw new IllegalStateException("API client not generated or initialized. Call /generate first.");
        }

        try {
            String requestUrl = config.getUrl();
            if (requestUrl == null || !requestUrl.matches("^(https?)://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")) {
                log.error("Invalid URL format: {}. Must be a fully qualified URL (e.g., https://example.com/path).", requestUrl);
                throw new IllegalArgumentException("Invalid URL format: " + requestUrl + ". Must be a fully qualified URL (e.g., https://example.com/path).");
            }

            String operationId = config.getOperationId();
            String httpMethod = config.getMethod() != null ? config.getMethod().toUpperCase() : "GET";
            log.info("Request URL: {}, OperationId: {}, HTTP Method: {}", 
                requestUrl, operationId, httpMethod);

            Method setBasePathMethod = apiClient.getClass().getMethod("setBasePath", String.class);
            setBasePathMethod.invoke(apiClient, requestUrl);

            Method setDebuggingMethod = apiClient.getClass().getMethod("setDebugging", boolean.class);
            setDebuggingMethod.invoke(apiClient, true);

            if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
                Method addDefaultHeaderMethod = apiClient.getClass().getMethod("addDefaultHeader", String.class, String.class);
                config.getHeaders().forEach((key, value) -> {
                    try {
                        log.debug("Adding header: {} = {}", key, value);
                        addDefaultHeaderMethod.invoke(apiClient, key, value);
                    } catch (Exception e) {
                        log.error("Failed to add header {}: {}", key, e.getMessage());
                    }
                });
            }

            log.info("Invoking operation: {}", operationId);
            Method apiMethod = findApiMethod(operationId);
            if (apiMethod == null) {
                log.error("No method found for operationId: {}", operationId);
                throw new UnsupportedOperationException("No method found for operationId: " + operationId);
            }

            Object[] parameters = prepareMethodParameters(apiMethod, config.getQueryParams(), config.getBody(), config.getBodies());
            log.debug("Calling API method {} with parameters: {}", apiMethod.getName(), Arrays.toString(parameters));

            StringBuilder queryString = new StringBuilder();
            if (config.getQueryParams() != null && !config.getQueryParams().isEmpty()) {
                queryString.append("?");
                config.getQueryParams().forEach((key, value) -> {
                    queryString.append(key).append("=").append(value).append("&");
                });
            }
            log.info("Expected request URL: {}{}", requestUrl, queryString.toString());

            Object result = apiMethod.invoke(defaultApi, parameters);
            log.info("Raw API response: {}", result);

            // Attempt to serialize/deserialize the response to ensure it's JSON-compatible
            try {
                String jsonResult = objectMapper.writeValueAsString(result);
                Object parsedResult = objectMapper.readValue(jsonResult, Object.class);
                log.debug("Parsed JSON response: {}", parsedResult);
                return parsedResult;
            } catch (Exception e) {
                log.warn("Failed to serialize/deserialize response, returning raw result: {}", e.getMessage());
                return result;
            }

        } catch (Exception e) {
            log.error("API call failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
            throw new RuntimeException("API call failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        }
    }

    private Method findApiMethod(String operationId) {
        log.info("Looking for method with operationId: {}", operationId);
        Class<?> defaultApiClass = defaultApi.getClass();
        String availableMethods = Arrays.stream(defaultApiClass.getMethods())
            .map(Method::getName)
            .collect(Collectors.joining(", "));
        log.info("Available methods in DefaultApi: {}", availableMethods);

        // Try raw operationId
        for (Method method : defaultApiClass.getMethods()) {
            if (method.getName().equalsIgnoreCase(operationId)) {
                log.info("Found method: {}", method.getName());
                return method;
            }
        }

        // Try camelCase version (e.g., postsOperation_GET -> postsOperationGet)
        String camelCaseOperationId = toCamelCase(operationId);
        for (Method method : defaultApiClass.getMethods()) {
            if (method.getName().equalsIgnoreCase(camelCaseOperationId)) {
                log.info("Found method (camelCase): {}", method.getName());
                return method;
            }
        }

        log.error("No method found for operationId: {} or camelCase: {}", operationId, camelCaseOperationId);
        return null;
    }

    private String toCamelCase(String operationId) {
        String[] parts = operationId.split("_");
        if (parts.length < 2) {
            return operationId;
        }
        StringBuilder camelCase = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                camelCase.append(part.substring(0, 1).toUpperCase())
                         .append(part.substring(1).toLowerCase());
            }
        }
        return camelCase.toString();
    }

    private Object[] prepareMethodParameters(Method method, Map<String, String> queryParams, Object body, Map<String, Object> bodies) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        String httpMethod = method.getName().contains("Get") ? "get" :
                           method.getName().contains("Post") ? "post" :
                           method.getName().contains("Put") ? "put" :
                           method.getName().contains("Patch") ? "patch" : null;

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            if ("body".equals(paramName)) {
                // Prioritize method-specific body from bodies map
                if (httpMethod != null && bodies != null && bodies.containsKey(httpMethod)) {
                    args[i] = bodies.get(httpMethod);
                    log.debug("Mapping method-specific body for {} to: {}", httpMethod, args[i]);
                } else if (body != null && !body.toString().isEmpty()) {
                    args[i] = body;
                    log.debug("Mapping fallback body to: {}", body);
                } else {
                    args[i] = null;
                    log.debug("No body provided for parameter: {}", paramName);
                }
            } else {
                // Remove special handling for "id" -> "user" mapping
                String queryParamKey = paramName;
                args[i] = queryParams != null ? queryParams.get(queryParamKey) : null;
                log.debug("Mapping parameter {} to value: {}", paramName, args[i]);
            }
            if (args[i] == null && !parameters[i].getType().isPrimitive()) {
                log.warn("Missing parameter: {}", paramName);
            }
        }
        return args;
    }
}