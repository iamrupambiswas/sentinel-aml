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

    // Phase 5
    public static final String RULES = API_V1 + "/rules";              // detection-service: rule admin (P13)
    public static final String ALERTS = API_V1 + "/alerts";            // alert-case-service: de-duplicated alerts (P14)
    public static final String OPS_DLQ = API_V1 + "/ops/dlq";          // detection-service: dead-letter browse/replay (P21)
    public static final String ACCOUNTS = API_V1 + "/accounts";        // customer-service: account risk rating
}