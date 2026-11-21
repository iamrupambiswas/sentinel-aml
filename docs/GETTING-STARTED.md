# Getting started: run Sentinel AML on your machine

This guide takes you from a fresh clone to a working system you can click through. **One command starts everything** (databases, broker, login server, the five Java services and the analyst console) in Docker. If you want to change the code, [Developing locally](#developing-locally) shows how to run parts of it from your machine instead.

**Time needed:** about 10 minutes the first time (image downloads and builds), under a minute after that.

> Already ran an older phase on this machine? Read [Upgrading from an earlier phase](#upgrading-from-an-earlier-phase) first.

---

## What you will have running

Everything below runs as Docker containers on one network, `sentinel-network`.

| Part | Container | Port on your machine | What it is |
|---|---|---|---|
| Analyst console | `sentinel-console` | **3000** | The web app you sign in to |
| API gateway | `sentinel-gateway` | **8080** | The single entry point for every API call, and the combined Swagger UI |
| customer, ingestion, detection, alert-case services | `sentinel-<name>-service` | 8082–8085 | The Java microservices |
| Keycloak | `sentinel-keycloak` | 8180 | Login server: users, roles, tokens |
| MySQL, Redis, ActiveMQ Artemis | `sentinel-mysql`, `-redis`, `-activemq` | 3306, 6379, 61616 (console 8161) | Databases, cache, message broker |
| Adminer, RedisInsight | `sentinel-adminer`, `-redisinsight` | 8081, 5540 | Optional admin UIs for MySQL and Redis |

---

## Step 0 — Install the tools (once)

| Tool | Version | Check it with |
|---|---|---|
| Docker Desktop (or OrbStack / Colima) | recent, **running**, at least **6 GB** of memory for Docker | `docker info` |
| Postman (optional) | any | for API testing |

That's all for running it. Java, Maven and Node.js are only needed for [developing locally](#developing-locally).

These ports must be free: **3000, 3306, 5540, 6379, 8080, 8081, 8082, 8083, 8084, 8085, 8161, 8180, 61616**. To see what is using a port (macOS/Linux):

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

---

## Step 1 — Start everything

From the repository root (the folder that contains `docker-compose.yml`):

```bash
docker compose up -d --build
```

The first run builds six images (the five services share one recipe, `services/Dockerfile`; the console uses `frontend/Dockerfile`) and downloads the rest. Later runs start in seconds.

Containers start in order: infrastructure first, then the services once MySQL, Redis, ActiveMQ and Keycloak are healthy, then the gateway once all four services are healthy, then the console. Watch them come up:

```bash
docker compose ps
```

You are ready when every container shows **(healthy)** (Adminer and RedisInsight just show *Up*). That takes about 1–2 minutes after the build. To follow one service's log:

```bash
docker compose logs -f detection-service
```

---

## Step 2 — Sign in

Open **http://localhost:3000** and click **Continue with Keycloak**. On Keycloak's page, use one of the test accounts. **The password is the same as the username.**

| Username | Role | What it can do |
|---|---|---|
| `analyst1` | Analyst | Work alerts, open cases, add notes, look up customers |
| `senior1` | Senior analyst | All of the above, plus close cases, reveal national IDs (with a reason), change risk ratings, read the audit trail |
| `admin1` | Administrator | Everything, plus change detection rules, handle dead letters, verify the audit chain |

You land on **Overview**. Your name and role are at the bottom of the left sidebar, next to the **Sign out** button. The three icons above your name switch the theme (system, light, dark).

How to use each page: the **[console user guide](console-user-guide.md)**.

---

## Step 3 — A five-minute tour

You need some data first. Keep the console open in your browser, then run these four commands in a terminal. They:

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

1. **Live alert.** Within about a second a **New alert** notification slides in at the top right, and the numbers update. No reload needed: the **Live** pill in the top bar shows the stream is connected.
2. **Alert.** Click the notification, or go to **Alerts** and click the account. You see why it fired (the explanation and evidence), the risk score, and a **Decision** panel. Type a note and click **Move to Under review**. A green notification confirms it.
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
| API reference (all services, Swagger) | http://localhost:8080/swagger-ui.html | Click **Authorize**, paste a token (Step 3 shows how to get one) |
| Keycloak admin console | http://localhost:8180/admin | admin / admin |
| Database (Adminer) | http://localhost:8081 | System **MySQL**, server **mysql**, user **root**, password **root** |
| Redis (RedisInsight) | http://localhost:5540 | First time: *Add Redis database* → host **redis**, port **6379** |
| Message broker (Artemis) | http://localhost:8161/console | admin / adminpassword |
| Health of one service | `curl localhost:8085/actuator/health` | none |

---

## Postman (optional)

1. In Postman: **Import** → select both files in `docs/postman/`.
2. Top right, choose the environment **Sentinel AML – Local**.
3. Right-click the collection → **Run collection** → **Run**. It signs in by itself and tests everything in about a minute.

Or from the command line, in the repository root:

```bash
npx newman run docs/postman/Sentinel-AML.postman_collection.json -e docs/postman/Sentinel-AML-local.postman_environment.json
```

The run adds test data (customers, alerts, cases) to your local database; that's harmless.

---

## Stopping

Stop everything (data is kept):

```bash
docker compose stop
```

Start again later with `docker compose up -d`. Add `--build` after pulling new code, so the images are rebuilt.

### Start completely fresh (deletes all local data)

```bash
docker compose down -v
```

This removes the containers, databases and queues. Next time, everything is created again from scratch.

---

## Developing locally

When you work on the code, run the part you're changing on your machine and keep the rest in Docker.

**Tools:** Java **21** (`java -version`), Maven 3.9+ (`mvn -v`), Node.js **20+** (`node -v`, 22 recommended).

### Infrastructure only, everything else on your machine

1. Start only the infrastructure and the two GUIs:

   ```bash
   docker compose up -d mysql redis activemq keycloak adminer redisinsight
   ```

2. Build the services (with them stopped: replacing a jar under a running service crashes it):

   ```bash
   cd services && mvn clean install -DskipTests
   ```

3. Start each service in its own terminal, from `services/`. Their defaults point at `localhost`, where the infrastructure ports are published:

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

   Check them all (every line should end in `{"status":"UP"}`):

   ```bash
   for p in 8080 8082 8083 8084 8085; do printf "$p "; curl -s localhost:$p/actuator/health; echo; done
   ```

4. Start the console in development mode, from `frontend/`:

   ```bash
   cp .env.example .env.local
   ```

   Open `frontend/.env.local` and replace the value after `NEXTAUTH_SECRET=` with a random string; `openssl rand -base64 32` prints one. Then:

   ```bash
   npm install
   ```
   ```bash
   npm run dev
   ```

   Ready when it prints `✓ Ready`. Don't run `npm run build` in the same folder while `npm run dev` is running: they share `frontend/.next`.

### One part on your machine, the rest in Docker

Stop that container and run your copy on the same port. For example, the console:

```bash
docker compose stop console
```

Then `npm run dev` in `frontend/` as above. For a Java service, `docker compose stop detection-service` and start its jar. The gateway in Docker can't see a service on your machine at `localhost`, so if you replace one of the four services behind it, run the gateway locally too (or recreate it with, for example, `CUSTOMER_SERVICE_URL=http://host.docker.internal:8082`).

---

## Upgrading from an earlier phase

Do this once after pulling this version:

1. **Stop any services or console you started by hand** (`Ctrl+C` in their terminals): the Docker containers now use the same ports.
2. **Start everything** (this also recreates Keycloak and the broker with their current settings):

   ```bash
   docker compose up -d --build
   ```

   Your data is kept: the databases and queues live in Docker volumes. The alert-case database gets any new tables automatically on start.
3. **Sign in again** in the console. Old sessions are cleared automatically.

---

## Troubleshooting

| What you see | Why | What to do |
|---|---|---|
| `Bind for 0.0.0.0:8080 failed: port is already allocated` (or 3000, 8082…) | Something you started by hand still uses the port | `lsof -nP -iTCP:8080 -sTCP:LISTEN`, stop that process, `docker compose up -d` again |
| A service stays `(health: starting)`, then restarts | Not enough memory for Docker (five JVMs, Keycloak, MySQL, Artemis) | Give Docker at least 6 GB, then `docker compose up -d` |
| `docker compose ps` shows a service *unhealthy* | It couldn't start | `docker compose logs <service>`; most often MySQL or the broker wasn't ready: `docker compose restart <service>` |
| Sign-in page shows *invalid client* or *invalid redirect uri* | Your Keycloak container is from before Phase 7 | `docker compose up -d --force-recreate keycloak`, wait 30 s, try again |
| Every API call returns 401 | Token from a different Keycloak, or expired | Get a new token (Step 3); tokens last 15 minutes |
| Top bar in the console stays on **Connecting** | alert-case-service or the gateway isn't healthy | `docker compose ps`, then `docker compose logs alert-case-service` |
| Lots of `429 Too Many Requests` from your own scripts | The gateway's rate limit (100 burst, 20 per second per user) | Slow down |
| Console pages return 500 after `npm run build` (local development) | `build` and `dev` share `frontend/.next` | Stop the console, `rm -rf frontend/.next`, start it again |
| A rebuilt image still runs old code | `up -d` without `--build` reuses images | `docker compose up -d --build` |

For deeper checks (SQL queries, Redis keys, broker queues per feature), see the [manual testing guide](manual-testing-guide.md). For why things are built the way they are, see the [architecture decision records](ADR/README.md).
