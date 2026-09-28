# Fraud Detection System

A local, asynchronous fraud-classification demo built with React, Spring Boot, Kafka, PostgreSQL, and FastAPI. It accepts a transaction, returns a tracking ID, and updates the decision as the event moves through the pipeline.

For the architecture walkthrough, request flow, reliability choices, and production trade-offs, see [System Design](system-design.md).

## Architecture at a glance

- **Dashboard:** React served by Nginx; polls the gateway for transaction status and reads model metrics from the inference API.
- **Transaction gateway:** validates submissions, persists lifecycle state, exposes the HTTP API, and produces/consumes Kafka events.
- **Kafka:** separates transaction intake from analysis with request, result, and dead-letter topics.
- **Fraud analyzer:** consumes requests, calls the inference service, and publishes lifecycle/result events.
- **ML inference:** FastAPI loads the selected scikit-learn/XGBoost artifact and returns a fraud decision and probability.
- **PostgreSQL:** stores the transaction summary and current status.

## Run locally

Requirements: Docker Engine, Docker Compose v2, and the model artifacts in `models/`. The training CSV is not required to run inference.

```bash
cp .env.example .env
```

Set a private local `POSTGRES_PASSWORD` in `.env`, then start the stack:

```bash
docker compose up --build
```

The services bind to loopback on these ports:

| Service | URL |
| --- | --- |
| Dashboard | http://localhost:3000 |
| Gateway API | http://localhost:8080 |
| Inference API docs | http://localhost:8000/docs |
| Inference health | http://localhost:8000/health |
| Model metrics | http://localhost:8000/metrics |

The dashboard proxies its API requests through Nginx. Kafka is available only inside the Compose network. PostgreSQL data persists in the `fraud-postgres-data` named volume; `docker compose down -v` removes it.

## Transaction API

- `POST /transactions` validates the request, stores `QUEUED`, and publishes it to Kafka. It returns `202 Accepted`; classification is asynchronous.
- `GET /transactions?limit=50` returns recent records (`limit` 1–100).
- `GET /transactions/{transactionId}` returns one transaction's current state and result.

The `features` array must contain exactly 30 finite numbers in dataset order (`Time`, `V1`–`V28`, `Amount`); `amount` must equal the last feature. Lifecycle states are `QUEUED`, `PROCESSING`, `COMPLETED`, and `FAILED`.

To send a synthetic request after startup:

```bash
python3 - <<'PY'
import json
from urllib.request import Request, urlopen

features = [0.0] * 30
payload = {
	"transactionId": "txn-demo-001",
	"cardId": "card-demo-001",
	"amount": features[-1],
	"timestamp": "2026-09-26T12:00:00Z",
	"features": features,
}
request = Request(
	"http://localhost:8080/transactions",
	data=json.dumps(payload).encode(),
	headers={"Content-Type": "application/json"},
	method="POST",
)
print(urlopen(request).read().decode())
PY
```

## Models and tests

XGBoost is the default model. Set `FRAUD_MODEL=logistic_regression` to select the baseline. Retraining requires Python and the local `creditcard.csv` dataset:

```bash
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r services/ml-inference-service/requirements-train.txt
python services/ml-inference-service/train.py
```

Run the focused test suites with:

```bash
python3 -m pip install -r services/ml-inference-service/requirements-test.txt
python3 -m pytest services/ml-inference-service/test_inference.py
mvn -f services/transaction-gateway/pom.xml test
cd frontend && npm ci && npm test && npm run build
```

For an end-to-end check, start the Compose stack and run `bash scripts/smoke-test-flow.sh`.

## Demo boundaries

Use synthetic transactions only. The local stack has no API authentication; Kafka uses PLAINTEXT inside the private Compose network, and the HTTP ports are loopback-bound. Before a public deployment, add authentication, authorization, TLS, rate limiting, secret management, and explicit database migrations. The gateway persists before publishing to Kafka, so a crash between those operations can leave a `QUEUED` record; a transactional outbox is the documented next step for stronger delivery guarantees.
