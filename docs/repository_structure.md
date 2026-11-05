# Sentinel AML - Repository Structure

This document outlines the monorepo directory structure for the Sentinel AML project. The repository is organized to cleanly separate the polyglot tech stack, isolating the Java microservices, the Next.js frontend, infrastructure as code, and testing suites.

## Directory Tree

```text
sentinel-aml/
├── docs/                   # Project documentation and architectural diagrams
│   └── ADR/                # Architecture Decision Records (e.g., ADR-001-microservices.md)
├── services/               # Java 21 / Spring Boot 3 backend services
│   ├── common-lib/         # Shared configurations, RFC 7807 error formats, and event DTOs
│   ├── gateway-service/    # Spring Cloud Gateway (Single entry point, rate limiting, JWT validation)
│   ├── customer-service/   # Manages customers, accounts, KYC, and PII masking
│   ├── ingestion-service/  # REST + CSV Batch ingestion, validation, FX to INR, and outbox pattern
│   ├── detection-service/  # Core AML rule engine, risk scoring, and hot-reloading
│   └── alert-case-service/ # Alert de-duplication, case management, and immutable hash-chained audit log
├── frontend/               # Next.js 14+ analyst console with Tailwind, shadcn/ui, and React Flow
├── infra/                  # Infrastructure as Code (Terraform)
│   ├── modules/            # Reusable AWS modules (VPC, EKS, RDS, ElastiCache, Amazon MQ, Secrets Manager)
│   └── envs/               
│       └── dev/            # Environment-specific state and variables for development
├── deploy/                 # Kubernetes manifests and Helm charts for CI/CD deployment
├── load-tests/             # JMeter test plans and synthetic CSV seed data for performance benchmarking
├── docker-compose.yml      # Local orchestration for MySQL, Redis, ActiveMQ, Keycloak, etc.
├── .env.example            # Template for 12-factor environment variables (No secrets in Git)
├── .gitignore              # Standard Git ignore rules for Java, Node, Terraform, etc.
└── README.md               # Project front page detailing business problem, architecture, and setup
```

## Module Responsibilities

### Backend (`/services`)
Each service within the `services/` directory operates as an independent bounded context. In accordance with the database-per-service pattern, they do not share databases; communication is handled asynchronously via ActiveMQ (events) or synchronously via REST (for gateway routing).

### Frontend (`/frontend`)
The analyst console connects to the backend via the `gateway-service`. It handles OAuth2/OIDC authentication flows, live alert rendering via Server-Sent Events (SSE), and case management interfaces.

### Infrastructure & Deployment (`/infra`, `/deploy`)
All AWS infrastructure is codified in Terraform under `infra/`. The `deploy/` directory bridges the gap between the application code and the infrastructure, providing the Helm charts required by the Jenkins CI/CD pipeline to deploy the microservices to Amazon EKS.