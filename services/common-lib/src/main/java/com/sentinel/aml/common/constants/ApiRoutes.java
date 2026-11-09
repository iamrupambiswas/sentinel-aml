package com.sentinel.aml.common.constants;

public final class ApiRoutes {

    private ApiRoutes() {}

    // Base API
    public static final String API_V1 = "/api/v1";

    // Service-Specific Routes
    public static final String TRANSACTIONS = API_V1 + "/transactions";
    public static final String CUSTOMERS = API_V1 + "/customers";
    public static final String ADMIN = API_V1 + "/admin";

    // Batch Ingestion
    public static final String INGESTION_BATCHES = API_V1 + "/ingestion/batches";
}