#!/usr/bin/env python3
import csv
import json
import random
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

GATEWAY_URL = "http://localhost:8080/transactions"
BASE_DIR = Path(__file__).resolve().parents[1]
CSV_PATH = BASE_DIR / "creditcard.csv"


def load_rows():
    rows = []
    with CSV_PATH.open("r", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            try:
                label = int(float(row["Class"]))
            except (KeyError, TypeError, ValueError):
                continue
            rows.append({**row, "Class": label})
    return rows


def build_payload_from_row(transaction_id: str, card_id: str, row: dict):
    features = [float(row["Time"])]
    for idx in range(1, 29):
        features.append(float(row[f"V{idx}"]))
    features.append(float(row["Amount"]))

    # The gateway validates that the last feature matches the amount field.
    amount = float(row["Amount"])
    return {
        "transactionId": transaction_id,
        "cardId": card_id,
        "amount": amount,
        "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "features": features,
    }


def send_transaction(payload: dict):
    body = json.dumps(payload).encode()
    req = Request(GATEWAY_URL, data=body, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urlopen(req, timeout=20) as resp:
            response_text = resp.read().decode()
            print(f"{payload['transactionId']} -> {resp.status} {response_text}")
    except Exception as exc:
        print(f"{payload['transactionId']} -> ERROR: {exc}")


def main():
    requested_count = int(sys.argv[1]) if len(sys.argv) > 1 else 12
    rows = load_rows()
    if not rows:
        raise SystemExit(f"No rows found in {CSV_PATH}.")

    fraud_rows = [row for row in rows if row["Class"] == 1]
    non_fraud_rows = [row for row in rows if row["Class"] == 0]

    if not fraud_rows:
        raise SystemExit("The CSV does not contain any Class=1 rows.")

    target_fraud = min(len(fraud_rows), max(1, requested_count // 3))
    target_non_fraud = min(len(non_fraud_rows), max(1, requested_count - target_fraud))

    selected = []
    selected.extend(random.sample(fraud_rows, target_fraud))
    selected.extend(random.sample(non_fraud_rows, target_non_fraud))

    random.shuffle(selected)
    selected = selected[:requested_count]

    for index, row in enumerate(selected, start=1):
        suffix = uuid.uuid4().hex[:8]
        transaction_id = f"txn-csv-{suffix}-{int(row['Class'])}"
        card_id = f"card-csv-{suffix}"
        payload = build_payload_from_row(transaction_id, card_id, row)
        send_transaction(payload)


if __name__ == "__main__":
    main()
