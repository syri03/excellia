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

@Service
public class DynamicApiCallerService {
    private static final Logger log = LoggerFactory.getLogger(DynamicApiCallerService.class);
    private Object apiClient;
    private Object defaultApi;
    private boolean isGenerated = false;

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
        if (!isGenerated) {
            log.error("API client not generated. Call /generate first.");
            throw new IllegalStateException("API client not generated. Call /generate first.");
        }

        try {
            validate(config); // Validate config before proceeding

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

            Object requestBody = getRequestBody(config);
            Object[] parameters = prepareMethodParameters(apiMethod, config.getQueryParams(), requestBody);
            log.debug("Calling API method {} with parameters: {}", apiMethod.getName(), parameters);

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
            return result;

        } catch (Exception e) {
            log.error("API call failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
            throw new RuntimeException("API call failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        }
    }

    private Method findApiMethod(String operationId) throws Exception {
        log.info("Looking for method with operationId: {}", operationId);
        Class<?> defaultApiClass = Class.forName("com.excellia.api.DefaultApi");
        String availableMethods = Arrays.stream(defaultApiClass.getMethods())
            .map(Method::getName)
            .collect(Collectors.joining(", "));
        log.info("Available methods in DefaultApi: {}", availableMethods);

        for (Method method : defaultApiClass.getMethods()) {
            if (method.getName().equalsIgnoreCase(operationId)) {
                log.info("Found method: {}", method.getName());
                return method;
            }
        }

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

    private Object[] prepareMethodParameters(Method method, Map<String, String> queryParams, Object body) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            if ("body".equals(paramName) && body != null && !body.toString().isEmpty()) {
                args[i] = body;
                log.debug("Mapping body parameter to: {}", body);
            } else {
                String queryParamKey = paramName.equals("id") && queryParams != null && queryParams.containsKey("user") ? "user" : paramName;
                args[i] = queryParams != null ? queryParams.get(queryParamKey) : null;
                log.debug("Mapping parameter {} (queried as {}) to value: {}", paramName, queryParamKey, args[i]);
            }
            if (args[i] == null && !parameters[i].getType().isPrimitive()) {
                log.warn("Missing parameter: {}", paramName);
            }
        }
        return args;
    }

    private Object getRequestBody(ApiConfig config) {
        String httpMethod = config.getMethod() != null ? config.getMethod().toLowerCase() : "get";
        if (config.getBodies() != null && config.getBodies().containsKey(httpMethod)) {
            log.debug("Using method-specific body for {}: {}", httpMethod, config.getBodies().get(httpMethod));
            return config.getBodies().get(httpMethod);
        }
        log.debug("Falling back to default body: {}", config.getBody());
        return config.getBody();
    }

    private void validate(ApiConfig config) {
        if (config.getMethods() != null && config.getBodies() != null) {
            for (String method : config.getMethods()) {
                if (!method.equalsIgnoreCase("delete") && !method.equalsIgnoreCase("get") && 
                    !config.getBodies().containsKey(method.toLowerCase())) {
                    log.warn("No body provided for method: {}. Consider adding it to the 'bodies' map.", method);
                }
            }
        }
    }
}