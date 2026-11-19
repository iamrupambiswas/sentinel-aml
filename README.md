# 🛡️ Sentinel AML
**Cloud-Native, Event-Driven Anti-Money Laundering (AML) Transaction Monitoring Platform**

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-14-black)](https://nextjs.org/)
[![AWS](https://img.shields.io/badge/AWS-EKS%20%7C%20RDS-orange)](https://aws.amazon.com/)

> **Project Vision:** Sentinel is a production-grade microservices platform that transforms a bank's 48–72 hour, spreadsheet-driven manual compliance review into a sub-second, real-time detection engine. It delivers explainable risk-scored alerts and an immutable, auditable case workflow for analysts.

---

## 🏗️ Architecture

The system utilizes an event-driven architecture with bounded contexts. Synchronous REST calls are reserved for the Gateway and UI, while the core detection pipeline is fully asynchronous to meet latency guarantees under high throughput.

```mermaid
flowchart TB
    %% Styling
    classDef frontend fill:#007acc,stroke:#005999,stroke-width:2px,color:#fff
    classDef gateway fill:#2b3a42,stroke:#1a2328,stroke-width:2px,color:#fff
    classDef service fill:#68a063,stroke:#4b7a46,stroke-width:2px,color:#fff
    classDef database fill:#f2a900,stroke:#cc8f00,stroke-width:2px,color:#fff
    classDef broker fill:#d94d3a,stroke:#a63a2b,stroke-width:2px,color:#fff
    classDef cache fill:#dc382d,stroke:#a8281e,stroke-width:2px,color:#fff
    classDef external fill:#555555,stroke:#333333,stroke-width:2px,color:#fff

    %% External & Identity Layer
    subgraph External [Clients & Identity]
        UI[Next.js Analyst Console<br/>React, Tailwind, SSE]:::frontend
        Bank[Bank Core Systems<br/>REST Stream / CSV Batch]:::external
        KC[Keycloak Auth<br/>OAuth2 / OIDC JWTs]:::external
    end

    %% Gateway Layer
    subgraph Gateway [Entry Point]
        SCG[Spring Cloud Gateway<br/>JWT Check, Routing, CORS]:::gateway
    end

    %% Compute / Microservices Layer (AWS EKS)
    subgraph Compute [Spring Boot 3 Microservices - AWS EKS]
        CUST[customer-svc<br/>KYC, PII Masking]:::service
        INGEST[ingestion-svc<br/>Validation, FX, Outbox]:::service
        DETECT[detection-svc<br/>Rule Engine, Risk Score]:::service
        ALERT[alert-case-svc<br/>De-dup, SAR Drafts, Audit]:::service
    end

    %% Event Bus & In-Memory Data
    subgraph FastState [Broker & Fast Memory]
        AMQ[(ActiveMQ Artemis<br/>Queues & DLQ)]:::broker
        REDIS[(Redis ElastiCache<br/>Sliding Windows, Limits)]:::cache
    end

    %% Persistence Layer (AWS RDS)
    subgraph Persistence [MySQL 8 Data Layer - AWS RDS]
        DB_CUST[(customer_db)]:::database
        DB_INGEST[(ingestion_db)]:::database
        DB_DETECT[(sentinel_detection)]:::database
        DB_ALERT[(alert_case_db)]:::database
    end

    %% Routing & Authentication
    UI -->|Authenticate| KC
    UI -->|HTTPS / SSE Stream| SCG
    Bank -->|HTTPS POST + JWT| SCG
    
    %% API Gateway to Services
    SCG -.->|Check Rate Limit| REDIS
    SCG -->|/api/v1/customers| CUST
    SCG -->|/api/v1/ingestion| INGEST
    SCG -->|/api/v1/alerts| ALERT
    SCG -->|/api/v1/rules, /api/v1/ops| DETECT

    %% Async Event Flow (The < 1s Pipeline)
    INGEST -->|1. Store Txn & Outbox| DB_INGEST
    INGEST -->|2. Publish txn.ingested| AMQ
    AMQ -->|3. Consume| DETECT
    
    DETECT <-->|4. Atomic Window Updates| REDIS
    DETECT -->|5. Write Rule Hit| DB_DETECT
    DETECT -->|6. Publish detection.hits| AMQ
    
    AMQ -->|7. Consume| ALERT
    ALERT -->|8. De-duplicate into one alert per pattern| DB_ALERT
    DETECT -.->|rules.changed topic: hot reload| AMQ
    CUST -.->|account risk ratings| REDIS
    
    CUST -->|Read/Write Profile| DB_CUST
```

## 🛠️ Technology Stack

| Domain | Technology | Justification |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3, Spring Cloud Gateway | Leverages virtual threads for high I/O concurrency. |
| **Frontend** | Next.js 14, TypeScript, TailwindCSS, NextAuth | Provides fast SSR, type safety, and real-time SSE components. |
| **Data Layer** | MySQL 8, Spring Data JPA, Flyway | Database-per-service pattern with repeatable migrations. |
| **State & Cache**| Redis | Sorted Sets for sliding windows, idempotency locks, rate limits. |
| **Messaging** | ActiveMQ Artemis | Guaranteed persistent delivery, DLQ routing, and topic broadcasting. |
| **Security** | Keycloak, Spring Security | OAuth2/OIDC RBAC and JWT validation. |
| **Cloud/Infra** | AWS (EKS, RDS, ElastiCache), Terraform | Reproducible infrastructure via IaC. |
| **Observability**| Prometheus, Grafana, OpenTelemetry | Tracks p95 latencies and distributed tracing across microservices. |

## 🔍 Core Detection Typologies (Rules)

The Detection Service utilizes a dynamic, hot-reloadable rule engine covering 6 critical AML typologies:
1. **CTR Threshold:** Single transactions exceeding a defined fiat equivalent.
2. **Structuring (Smurfing):** Multiple transactions just below reporting thresholds within 24 hours.
3. **Rapid Movement / Layering:** High percentage of received funds exiting the account within 48 hours.
4. **High-Risk Jurisdictions:** Transfers involving sanctioned entities (fuzzy matched) or listed countries.
5. **Behavioral Deviation:** Daily transactional value significantly exceeding a 90-day rolling baseline.
6. **Round Amount:** Recurring high-value, exact round-number transfers.

## 🚀 Performance Benchmarks (JMeter)

*(Note: These values will be populated in Phase 9 after rigorous load testing).*

| Scenario | Load | Target Metric | Achieved Metric |
| :--- | :--- | :--- | :--- |
| **Streaming Ingestion** | 200 TPS | End-to-End Latency p95 < 1s | *TBD* |
| **Batch Bulk Upload** | 10,000 txns | Processing Time < 2 min | *TBD* |
| **Rule Hot-Reload** | Live Topic | Time to active < 5s | *TBD* |

## 💻 Local Setup & Development

This repository relies on Docker Compose to orchestrate local backing services before running the Java applications.

**1. Clone the repository**
```bash
git clone https://github.com/yourusername/sentinel-aml.git
cd sentinel-aml
```

**2. Start the infrastructure (Docker)**
```bash
docker compose up -d
```
Wait until `docker compose ps` shows mysql, redis and activemq as `(healthy)`.

**3. Build the services**
```bash
cd services && mvn clean install -DskipTests
```

**4. Start each service in its own terminal** (from `services/`)
```bash
java -jar customer-service/target/customer-service-1.0.0-SNAPSHOT.jar
```
```bash
java -jar ingestion-service/target/ingestion-service-1.0.0-SNAPSHOT.jar
```
```bash
java -jar detection-service/target/detection-service-1.0.0-SNAPSHOT.jar
```
```bash
java -jar alert-case-service/target/alert-case-service-1.0.0-SNAPSHOT.jar
```
```bash
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar
```

**5. Test** with the Postman collection in `docs/postman/`. The step-by-step guide, including how to verify each request in MySQL, Redis, ActiveMQ and the logs, is in [`docs/manual-testing-guide.md`](docs/manual-testing-guide.md).

**6. Tests** (alert-case-service's 50-thread de-dup test starts a MySQL Testcontainer, so Docker must be running; without Docker it is skipped)
```bash
cd services && mvn test
```

## 🖥️ Local web UIs & routes

### Container consoles

| Container | Web UI | Login | Notes |
|---|---|---|---|
| MySQL (`sentinel-mysql`, :3306) | **Adminer** → http://localhost:8081 | System `MySQL`, server `mysql`, user `root`, password `root` | Databases: `customer_db`, `ingestion_db`, `sentinel_detection`, `alert_case_db` |
| Redis (`sentinel-redis`, :6379) | **RedisInsight** → http://localhost:5540 | none | First time: *Add Redis database* → host `redis`, port `6379` |
| ActiveMQ Artemis (`sentinel-activemq`, :61616) | **Artemis console** → http://localhost:8161/console | `admin` / `adminpassword` | *Artemis* tab → *Queues*: `sentinel.txn.ingested`, `sentinel.detection.hits`, topic `sentinel.rules.changed`, `DLQ.sentinel.*` (redelivery policy: `infra/artemis/broker.properties`) |
| Keycloak (`sentinel-keycloak`) | **Admin console** → http://localhost:8180/admin | `admin` / `admin` | Realm `sentinel` is imported on start from `infra/keycloak/sentinel-realm.json` (roles, clients, test users) |

Command-line alternatives: `docker exec -it sentinel-mysql mysql -uroot -proot --default-character-set=utf8mb4` and `docker exec -it sentinel-redis redis-cli`.

### Services

| Service | Port | Swagger UI | Actuator |
|---|---|---|---|
| gateway-service | **8080** (use this for API calls) | – | `/actuator/health`, `/actuator/info` |
| customer-service | 8082 | http://localhost:8082/swagger-ui.html | `/actuator/health` |
| ingestion-service | 8083 | http://localhost:8083/swagger-ui.html | `/actuator/health`, `/info`, `/metrics` |
| detection-service | 8084 | http://localhost:8084/swagger-ui.html | `/actuator/health`, `/info`, `/metrics` |
| alert-case-service | 8085 | http://localhost:8085/swagger-ui.html | `/actuator/health`, `/info`, `/metrics` |

Raw OpenAPI JSON for each service: `http://localhost:<port>/v3/api-docs`.

### API routes (all reachable through the gateway on :8080)

| Method | Route | Service | Purpose |
|---|---|---|---|
| POST | `/api/v1/customers` | customer | Onboard a customer (PII encrypted, masked response) |
| GET | `/api/v1/customers/{id}` | customer | Customer with accounts and effective risk ratings |
| POST | `/api/v1/customers/{id}/accounts` | customer | Open an account (optional explicit `accountId`, e.g. `ACC-RAPID-01`) |
| PUT | `/api/v1/customers/{id}/risk-rating` | customer | Change customer risk; republishes every account to Redis |
| GET / PUT | `/api/v1/accounts/{id}` · `/api/v1/accounts/{id}/risk-rating` | customer | Account view / account risk change |
| POST | `/api/v1/admin/seed-data` | customer | Generate 5,000 synthetic customers (then syncs risk ratings to Redis) |
| POST | `/api/v1/admin/risk-sync` | customer | Full resync of `aml:acct-risk:*` |
| POST | `/api/v1/transactions` | ingestion | Ingest one transaction (`Idempotency-Key` header required) |
| POST | `/api/v1/ingestion/batches` | ingestion | Upload a CSV (multipart field `file`) |
| GET | `/api/v1/ingestion/batches/{id}` | ingestion | Batch status + rejected-row report |
| GET | `/api/v1/rules` · `/api/v1/rules/{code}` · `/api/v1/rules/{code}/versions` | detection | All rules with DB and live-loaded version; audit history |
| PUT | `/api/v1/rules/{code}` | detection | Change a rule (`expectedVersion`, `changedBy`, `reason`); live on every pod in ms |
| POST | `/api/v1/detection/rules/reload` | detection | Reload rules and watchlist from the DB |
| POST | `/api/v1/detection/baseline/recompute` | detection | Run the 90-day baseline job now |
| GET | `/api/v1/ops/dlq` · `/api/v1/ops/dlq/{queue}` | detection | Dead-letter depth / browse |
| POST · DELETE | `/api/v1/ops/dlq/{queue}/replay` · `/api/v1/ops/dlq/{queue}/messages/{id}` | detection | Replay to the source queue / delete one |
| GET | `/api/v1/alerts?status=&accountId=&ruleCode=` · `/api/v1/alerts/{id}` · `/api/v1/alerts/stats` | alert-case | De-duplicated alerts, detail with hits, noise statistics |
| PATCH | `/api/v1/alerts/{id}/status` | alert-case | Analyst workflow (NEW → UNDER_REVIEW → ESCALATED → CLOSED_*) |

Detection metrics: `http://localhost:8084/actuator/metrics/sentinel_ingest_to_detection_latency`, `sentinel_detection_latency`, `sentinel_rule_hits_total`, `sentinel_rule_reload_lag`. Alert metrics (:8085): `sentinel_alerts_created_total`, `sentinel_alert_hits_merged_total`, `sentinel_alert_hits_duplicate_total`.

Flow diagrams for every route: open `docs/flows/index.html` in a browser.

## 📜 Documentation

* **Architecture Decision Records (ADRs):** View our major technical decisions in `docs/ADR`.
* **Repository Structure:** See `docs/repository_structure.md` for a breakdown of the monorepo layout.
* **Manual run & test guide:** `docs/manual-testing-guide.md` (step by step, with the Postman collection in `docs/postman/`).
* **Flow documentation:** Open `docs/flows/index.html` for per-flow diagrams with the classes, tables, Redis keys and queues involved.
* **API Specs:** Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the Gateway is running.