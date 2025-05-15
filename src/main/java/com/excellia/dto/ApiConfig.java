package com.excellia.dto;

import java.util.List;
import java.util.Map;

public class ApiConfig {
    private String url;
    private String method;
    private List<String> methods;
    private String operationId;
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Object body;
    private Map<String, Object> bodies;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getBodies() {
        return bodies;
    }

    public void setBodies(Map<String, Object> bodies) {
        this.bodies = bodies;
    }
}