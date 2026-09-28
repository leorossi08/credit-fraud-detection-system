import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";

const metrics = {
  active_model: "xgboost",
  cross_validation: {
    fold_count: 5,
    metrics: {
      xgboost: {
        average_precision: { mean: 0.84, std: 0.03 },
        precision_at_0_5: { mean: 0.94, std: 0.02 },
        recall_at_0_5: { mean: 0.78, std: 0.04 },
      },
    },
  },
  test_metrics_at_threshold_0_5: {
    xgboost: { average_precision: 0.86, f1: 0.85 },
  },
};

const fraudTransaction = {
  transactionId: "txn-fraud-1",
  cardId: "card-demo-1",
  amount: 84.5,
  timestamp: "2026-09-26T12:00:00Z",
  status: "COMPLETED",
  isFraud: true,
  fraudProbability: 0.97,
  message: null,
  createdAt: "2026-09-26T12:00:00Z",
  updatedAt: "2026-09-26T12:00:01Z",
};

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  }));
}

describe("Fraud operations dashboard", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url === "/api/model/metrics") return jsonResponse(metrics);
      if (url === "/api/transactions" && init?.method === "POST") {
        return jsonResponse({ transactionId: "txn-created", status: "QUEUED" }, 202);
      }
      if (url.startsWith("/api/transactions")) return jsonResponse([fraudTransaction]);
      return jsonResponse({}, 404);
    }));
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("loads activity and displays model cross-validation quality", async () => {
    render(<App />);

    expect(await screen.findByText("txn-fraud-1")).toBeTruthy();
    expect(screen.getByText("5-fold stratified CV")).toBeTruthy();
    expect(screen.getByText("0.840")).toBeTruthy();
  });

  it("filters fraud transactions and shows their result details", async () => {
    render(<App />);
    await screen.findByText("txn-fraud-1");

    fireEvent.click(screen.getByRole("tab", { name: /Fraud alerts/ }));
    fireEvent.click(screen.getByText("txn-fraud-1"));

    expect(screen.getAllByText("Fraud flagged")).toHaveLength(2);
    expect(screen.getAllByText("97.0%")).toHaveLength(2);
  });

  it("submits a clearly synthetic demo transaction", async () => {
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "New demo transaction" }));
    fireEvent.change(screen.getByPlaceholderText("e.g. card-demo-01"), { target: { value: "card-new" } });
    fireEvent.click(screen.getByRole("button", { name: "Submit for analysis" }));

    await waitFor(() => {
      const calls = vi.mocked(fetch).mock.calls;
      expect(calls.some(([url, options]) => url === "/api/transactions" && options?.method === "POST")).toBe(true);
    });
  });
});