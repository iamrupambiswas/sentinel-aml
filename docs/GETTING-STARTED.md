# Getting started: run Sentinel AML on your machine

This guide takes you from a fresh clone to a working system you can click through: the infrastructure in Docker, five Java services, and the analyst console in your browser. Follow the steps in order. Every command is meant to be copied as-is.

**Time needed:** about 15 minutes the first time (downloads), 2 minutes after that.

> Already ran an older phase on this machine? Read [Upgrading from an earlier phase](#upgrading-from-an-earlier-phase) first.

---

## What you will have running

| Part | Where | What it is |
|---|---|---|
| MySQL, Redis, ActiveMQ Artemis, Keycloak, Adminer, RedisInsight | Docker containers | Databases, cache, message broker, login server and two admin UIs |
| customer, ingestion, detection, alert-case services | Your machine, ports 8082–8085 | The Java microservices |
| gateway | Your machine, port **8080** | The single entry point for every API call |
| Analyst console | Your machine or Docker, port **3000** | The web app you sign in to |

---

## Step 0 — Install the tools (once)

| Tool | Version | Check it with | Get it |
|---|---|---|---|
| Docker Desktop | recent, **running** | `docker info` | docker.com |
| Java (JDK) | **21** | `java -version` | e.g. Temurin 21 |
| Maven | 3.9+ | `mvn -v` | maven.apache.org |
| Node.js | **20 or newer** (22 recommended) | `node -v` | nodejs.org or nvm |
| Postman (optional) | any | — | for API testing |

These ports must be free: **3000, 3306, 5540, 6379, 8080, 8081, 8082, 8083, 8084, 8085, 8161, 8180, 61616**. To see what is using a port (macOS/Linux):

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

---

## Step 1 — Start the infrastructure

From the repository root (the folder that contains `docker-compose.yml`):

```bash
docker compose up -d
```

Wait until it is healthy, about 30–60 s the first time:

```bash
docker compose ps
```

You are ready when `sentinel-mysql`, `sentinel-redis` and `sentinel-activemq` show **(healthy)** and `sentinel-keycloak` shows **Up**. Keycloak needs about 30 s more to finish loading. This should print `"sentinel"`:

```bash
curl -s localhost:8180/realms/sentinel | grep -o '"realm":"[a-z]*"'
```

---

## Step 2 — Build the Java services

```bash
cd services
```
```bash
mvn clean install -DskipTests
```

`BUILD SUCCESS` at the end means it worked. (Leave out `-DskipTests` to run all tests too; that takes a few minutes and needs Docker running.)

> Rebuild only while the services are **stopped**: replacing a jar under a running service crashes it.

---

## Step 3 — Start the five services

Open **five terminal windows**, each in the `services` folder, and run one command per window. The order below keeps the logs easy to follow; any order works.

**Terminal 1 — customer-service (8082)**
```bash
java -jar customer-service/target/customer-service-1.0.0-SNAPSHOT.jar
```
**Terminal 2 — ingestion-service (8083)**
```bash
java -jar ingestion-service/target/ingestion-service-1.0.0-SNAPSHOT.jar
```
**Terminal 3 — detection-service (8084)**
```bash
java -jar detection-service/target/detection-service-1.0.0-SNAPSHOT.jar
```
**Terminal 4 — alert-case-service (8085)**
```bash
java -jar alert-case-service/target/alert-case-service-1.0.0-SNAPSHOT.jar
```
**Terminal 5 — gateway (8080)**
```bash
java -jar gateway-service/target/gateway-service-1.0.0-SNAPSHOT.jar
```

Each one is ready when its window prints `Started …Application in … seconds`. Check all five at once (from any terminal):

```bash
for p in 8080 8082 8083 8084 8085; do printf "$p "; curl -s localhost:$p/actuator/health; echo; done
```

Every line should end in `{"status":"UP"}`.

---

## Step 4 — Start the analyst console

Pick **one** of the two ways.

### Option A — Development mode (recommended while working on the code)

From the repository root:

```bash
cd frontend
```
```bash
cp .env.example .env.local
```

Now open `frontend/.env.local` in an editor and replace `change-me-to-a-long-random-string` after `NEXTAUTH_SECRET=` with a random value. This command prints one you can paste:

```bash
openssl rand -base64 32
```

Then install and start (the first `npm install` takes a minute):

```bash
npm install
```
```bash
npm run dev
```

Ready when it prints `✓ Ready`. Leave this terminal open; `Ctrl+C` stops the console.

### Option B — Docker (no Node.js needed)

From the repository root:

```bash
docker compose --profile console up -d --build console
```

The first build takes 2–4 minutes. Ready when this shows **(healthy)**:

```bash
docker compose ps console
```

To stop it later: `docker compose stop console`.

> Don't run both options at once: both use port 3000.

---

## Step 5 — Sign in

Open **http://localhost:3000** and click **Continue with Keycloak**. On Keycloak's page, use one of the test accounts. **The password is the same as the username.**

| Username | Role | What it can do |
|---|---|---|
| `analyst1` | Analyst | Work alerts, open cases, add notes, look up customers |
| `senior1` | Senior analyst | All of the above, plus close cases, reveal national IDs (with a reason), change risk ratings, read the audit trail |
| `admin1` | Administrator | Everything, plus change detection rules, handle dead letters, verify the audit chain |

You land on **Overview**. Your name and role are at the bottom of the left sidebar, next to the **Sign out** button.

**Theme:** the three icons above your name switch between *System* (follows your computer), *Light* and *Dark*. The choice is remembered.

---

## Step 6 — A five-minute tour

You need some data first. Keep the console open in your browser, then run these four commands in a new terminal. They:

1. get an admin token,
2. register a customer,
3. open a **high-risk** account for them, and
4. send one large cash deposit into it. It's over the ₹8,30,000 reporting threshold, so it raises an alert.

(They need `curl` and `python3`, which macOS and most Linux systems have.)

```bash
TOKEN=$(curl -s -d grant_type=password -d client_id=sentinel-postman -d username=admin1 -d password=admin1 localhost:8180/realms/sentinel/protocol/openid-connect/token | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])"); ACC=ACC-TOUR-$(date +%H%M%S); echo "Your account: $ACC"
```
```bash
CUSTOMER=$(curl -s -X POST localhost:8080/api/v1/customers -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"type":"RETAIL","fullName":"Tour Customer","idNumber":"PAN-TOUR1234Z","dob":"1985-06-15","nationality":"IND"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['id'])"); echo "Customer: $CUSTOMER"
```
```bash
curl -s -X POST localhost:8080/api/v1/customers/$CUSTOMER/accounts -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d "{\"accountId\":\"$ACC\",\"type\":\"SAVINGS\",\"currency\":\"INR\",\"riskRating\":\"HIGH\"}"
```
```bash
curl -s -X POST localhost:8080/api/v1/transactions -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Idempotency-Key: tour-$(date +%s)" -d "{\"sourceAccountId\":\"EXT-CASH\",\"destinationAccountId\":\"$ACC\",\"amount\":1200000,\"currency\":\"INR\",\"type\":\"CASH_DEPOSIT\"}"
```

Run all four in the **same** terminal: the later commands reuse `$TOKEN`, `$ACC` and `$CUSTOMER`. The last one answers `Transaction accepted for asynchronous processing`. The alert's risk score will be **39.0**: the rule's 30 points × 1.3 for the high-risk customer.

Then, in the console:

1. **Live alert.** Within about a second a **New alert · ACC-TOUR-…** notice slides in at the top right, and the numbers update. No reload needed: the dot next to **Live** in the top bar shows the stream is connected.
2. **Alert.** Click the notice, or go to **Alerts** and click the account. You see why it fired (the explanation and evidence), the risk score, and a **Decision** panel. Type a note and click **Move to Under review**.
3. **Case.** On the same page, in the **Case** panel, click **Open case**. You are now on the case page. Click **Assign to me**, then **Start investigating**, then add a note.
4. **Close the case.** Closing needs a senior analyst. Sign out (bottom left), sign in as `senior1`, open **Cases**, pick your case, write a resolution and click **Close · no action needed**. The **History** panel lists every step with who did it.
5. **Customer and PII.** As `senior1`, open the alert again and click its **Account** link (or go to **Customers** and search your `ACC-TOUR-…` account). You see *Tour Customer* with the national ID masked. Click **Reveal**, give a reason, and the full value shows for 60 seconds. As `analyst1` there is no Reveal button.
6. **Audit trail.** As `senior1`, open **Audit trail**: your reveal and every case step are there, from all services. Sign in as `admin1` and click **Verify chain**: it recomputes every hash and confirms nothing was changed.
7. **Rules and dead letters.** As `admin1`, **Detection rules** lets you change a threshold (with a reason; it's live on every detection pod in seconds). **Dead letters** shows messages that failed five times, with Replay and Delete.

---

## Other things you can open

| What | Address | Login |
|---|---|---|
| Analyst console | http://localhost:3000 | analyst1 / senior1 / admin1 |
| API reference (all services, Swagger) | http://localhost:8080/swagger-ui.html | Click **Authorize**, paste a token (Step 6 shows how to get one) |
| Keycloak admin console | http://localhost:8180/admin | admin / admin |
| Database (Adminer) | http://localhost:8081 | System **MySQL**, server **mysql**, user **root**, password **root** |
| Redis (RedisInsight) | http://localhost:5540 | First time: *Add Redis database* → host **redis**, port **6379** |
| Message broker (Artemis) | http://localhost:8161/console | admin / adminpassword |

---

## Postman (optional)

1. In Postman: **Import** → select both files in `docs/postman/`.
2. Top right, choose the environment **Sentinel AML – Local**.
3. Right-click the collection → **Run collection** → **Run**. Everything is tested in about a minute; it signs in by itself.

Or from the command line, in the repository root:

```bash
npx newman run docs/postman/Sentinel-AML.postman_collection.json -e docs/postman/Sentinel-AML-local.postman_environment.json
```

Expected: **125 requests, 208 assertions, 0 failures.** The run adds test data (customers, alerts, cases) to your local database; that's harmless.

---

## Stopping

1. Console: `Ctrl+C` in its terminal (Option A) or `docker compose stop console` (Option B).
2. Services: `Ctrl+C` in each of the five terminals.
3. Infrastructure (data is kept):

```bash
docker compose stop
```

To start again later: Step 1, Step 3, Step 4 (no rebuild needed unless the code changed).

### Start completely fresh (deletes all local data)

```bash
docker compose down -v
```

This removes the databases and queues. Next time, everything is created again from scratch.

---

## Upgrading from an earlier phase

Do this once after pulling this version:

1. **Recreate Keycloak** so it gets the console's login client and its fixed public address. This re-creates the test users too:

   ```bash
   docker compose up -d keycloak
   ```

2. **Rebuild the services** (Step 2) with all services stopped. The alert-case database gets new tables (cases, audit log) automatically on start.
3. **Sign in again** in the console. Old sessions are cleared automatically.

---

## Troubleshooting

| What you see | Why | What to do |
|---|---|---|
| Sign-in page shows *invalid client* or *invalid redirect uri* | Your Keycloak container is from before Phase 7 | `docker compose up -d keycloak`, wait 30 s, try again |
| Console says "Could not reach the API" | The gateway (8080) isn't running | Start it (Step 3, terminal 5) |
| Top bar stays on **Connecting** | alert-case-service isn't running | Start it (Step 3, terminal 4) |
| Every page sends you back to sign in | `NEXTAUTH_SECRET` missing in `frontend/.env.local` | Redo Step 4, Option A |
| `Port 3000 is in use` | The other console option is still running | Stop one of them |
| A service exits with `AMQ219007` / `Cannot connect` | ActiveMQ not up yet | `docker compose ps`, wait for *healthy*, start the service again |
| A service exits with a Flyway / MySQL error | MySQL not up yet | Same as above |
| Lots of `429 Too Many Requests` from your own scripts | The gateway's rate limit (100 burst, 20 per second per user) | Slow down |
| Console pages return 500 after `npm run build` | `build` and `dev` share `frontend/.next` | Stop the console, `rm -rf frontend/.next`, start it again |

For deeper checks (SQL queries, Redis keys, broker queues per feature), see the [manual testing guide](manual-testing-guide.md). For why things are built the way they are, see the [architecture decision records](ADR/README.md).
