import { useEffect, useState, type FormEvent } from "react";
import {
  Activity,
  AlertTriangle,
  ArrowDownRight,
  ArrowUpRight,
  Check,
  ChevronRight,
  CircleHelp,
  Clock3,
  Database,
  Fingerprint,
  Gauge,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";

type Status = "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
type Filter = "ALL" | Status | "FRAUD";

interface Transaction {
  transactionId: string;
  cardId: string;
  amount: number;
  timestamp: string;
  status: Status;
  isFraud: boolean | null;
  fraudProbability: number | null;
  message: string | null;
  createdAt: string;
  updatedAt: string;
}

interface MetricSummary {
  mean: number;
  std: number;
}

interface ModelMetrics {
  active_model: string;
  cross_validation: {
    fold_count: number;
    metrics: Record<string, Record<string, MetricSummary>>;
  };
  test_metrics_at_threshold_0_5: Record<string, Record<string, number>>;
}

const FILTERS: { label: string; value: Filter }[] = [
  { label: "All activity", value: "ALL" },
  { label: "Queued", value: "QUEUED" },
  { label: "Processing", value: "PROCESSING" },
  { label: "Completed", value: "COMPLETED" },
  { label: "Fraud alerts", value: "FRAUD" },
  { label: "Failed", value: "FAILED" },
];

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(value);
}

function formatPercent(value: number | null) {
  return value === null ? "--" : `${(value * 100).toFixed(1)}%`;
}

function maskCardReference(cardId: string) {
  return cardId.length > 4 ? `••••${cardId.slice(-4)}` : "••••";
}

function App() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [metrics, setMetrics] = useState<ModelMetrics | null>(null);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [transactionId, setTransactionId] = useState("");
  const [cardId, setCardId] = useState("");
  const [amount, setAmount] = useState("84.50");

  async function refreshTransactions() {
    setIsRefreshing(true);
    try {
      const response = await fetch("/api/transactions?limit=100");
      if (!response.ok) throw new Error(`Gateway returned ${response.status}`);
      const data = (await response.json()) as Transaction[];
      setTransactions(data);
      setError(null);
    } catch {
      setError("Could not reach the gateway. Start the services and refresh.");
    } finally {
      setIsRefreshing(false);
    }
  }

  async function refreshMetrics() {
    try {
      const response = await fetch("/api/model/metrics");
      if (!response.ok) throw new Error(`Inference service returned ${response.status}`);
      setMetrics((await response.json()) as ModelMetrics);
    } catch {
      setMetrics(null);
    }
  }

  useEffect(() => {
    void refreshTransactions();
    void refreshMetrics();
    const intervalId = window.setInterval(() => {
      void refreshTransactions();
    }, 5000);
    return () => window.clearInterval(intervalId);
  }, []);

  const visibleTransactions = transactions.filter((transaction) => {
    const matchesFilter =
      filter === "ALL" ||
      (filter === "FRAUD"
        ? transaction.isFraud === true
        : transaction.status === filter);
    const normalizedQuery = query.trim().toLowerCase();
    const matchesQuery =
      !normalizedQuery ||
      transaction.transactionId.toLowerCase().includes(normalizedQuery) ||
      transaction.cardId.toLowerCase().includes(normalizedQuery);
    return matchesFilter && matchesQuery;
  });

  const selectedTransaction = transactions.find(
    (transaction) => transaction.transactionId === selectedId,
  );
  const fraudCount = transactions.filter((transaction) => transaction.isFraud === true).length;
  const activeCount = transactions.filter(
    (transaction) => transaction.status === "QUEUED" || transaction.status === "PROCESSING",
  ).length;
  const completedTransactions = transactions.filter((transaction) => transaction.status === "COMPLETED");
  const averageRisk = completedTransactions.length
    ? completedTransactions.reduce((total, transaction) => total + (transaction.fraudProbability ?? 0), 0) /
      completedTransactions.length
    : null;

  async function submitTransaction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const numericAmount = Number(amount);
    if (!Number.isFinite(numericAmount) || numericAmount < 0) {
      setError("Enter a valid non-negative amount.");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    const features = Array<number>(30).fill(0);
    features[0] = Math.floor(Date.now() / 1000);
    features[29] = numericAmount;
    const payload = {
      transactionId: transactionId.trim() || `demo-${crypto.randomUUID().slice(0, 8)}`,
      cardId: cardId.trim(),
      amount: numericAmount,
      timestamp: new Date().toISOString(),
      features,
    };

    try {
      const response = await fetch("/api/transactions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => null)) as { detail?: string; error?: string } | null;
        throw new Error(body?.detail ?? body?.error ?? `Request failed (${response.status})`);
      }
      const result = (await response.json()) as { transactionId: string };
      setSelectedId(result.transactionId);
      setIsDialogOpen(false);
      setTransactionId("");
      setCardId("");
      await refreshTransactions();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Could not submit transaction.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="app-shell">
      <aside className="side-rail" aria-label="Primary navigation">
        <div className="brand-mark"><ShieldCheck size={21} strokeWidth={2.2} /></div>
        <nav className="rail-nav">
          <a className="rail-link active" href="#overview" aria-label="Overview" title="Overview"><Activity size={19} /></a>
          <a className="rail-link" href="#model-quality" aria-label="Model quality" title="Model quality"><Gauge size={19} /></a>
          <a className="rail-link" href="#data-status" aria-label="Data status" title="Data status"><Database size={19} /></a>
        </nav>
        <div className="rail-bottom">
          <div className="avatar" aria-label="Local demo environment">LD</div>
        </div>
      </aside>

      <main className="workspace" id="overview">
        <header className="topbar">
          <div className="breadcrumb"><span>Risk systems</span><ChevronRight size={14} /><strong>Fraud operations</strong></div>
          <div className="topbar-right">
            <span className="environment"><span className="environment-dot" /> LOCAL DEMO</span>
            <span className="topbar-divider" />
            <span className="updated-label"><Clock3 size={14} /> Refreshes every 5 seconds</span>
          </div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div>
              <div className="eyebrow"><span className="eyebrow-rule" /> TRANSACTION MONITORING</div>
              <h1>Fraud operations</h1>
              <p className="page-subtitle">Review transaction flow, model decisions, and processing health.</p>
            </div>
            <button className="primary-button" onClick={() => setIsDialogOpen(true)} type="button"><Plus size={17} /> New demo transaction</button>
          </section>

          {error && (
            <div className="notice error-notice" role="alert">
              <AlertTriangle size={17} /> <span>{error}</span>
              <button className="icon-button notice-close" onClick={() => setError(null)} aria-label="Dismiss error" type="button"><X size={16} /></button>
            </div>
          )}

          <section className="metrics-grid" aria-label="Transaction summary">
            <article className="metric-block">
              <div className="metric-top"><span>Transactions in view</span><Activity size={17} /></div>
              <strong>{transactions.length}</strong>
              <small><span className="metric-neutral"><Database size={12} /> Latest 100 records</span></small>
            </article>
            <article className="metric-block">
              <div className="metric-top"><span>Awaiting analysis</span><Clock3 size={17} /></div>
              <strong>{activeCount}</strong>
              <small><span className="metric-neutral">Queued or processing</span></small>
            </article>
            <article className="metric-block risk-metric">
              <div className="metric-top"><span>Fraud flagged</span><AlertTriangle size={17} /></div>
              <strong>{fraudCount}</strong>
              <small><span className="metric-risk"><ArrowUpRight size={13} /> Confirmed model flags</span></small>
            </article>
            <article className="metric-block">
              <div className="metric-top"><span>Average fraud score</span><Gauge size={17} /></div>
              <strong>{formatPercent(averageRisk)}</strong>
              <small><span className="metric-neutral"><ArrowDownRight size={13} /> Completed records in view</span></small>
            </article>
          </section>

          <section className="activity-section" id="data-status">
            <div className="section-heading">
              <div><div className="section-kicker">LIVE WORK QUEUE</div><h2>Recent transactions</h2></div>
              <button className={`quiet-button${isRefreshing ? " refreshing" : ""}`} onClick={() => void refreshTransactions()} type="button" disabled={isRefreshing}><RefreshCw size={15} /> Refresh</button>
            </div>

            <div className="table-toolbar">
              <div className="filter-tabs" role="tablist" aria-label="Filter transactions">
                {FILTERS.map((item) => (
                  <button key={item.value} className={`filter-tab${filter === item.value ? " selected" : ""}`} onClick={() => setFilter(item.value)} role="tab" aria-selected={filter === item.value} type="button">
                    {item.label}
                    {item.value === "FRAUD" && fraudCount > 0 && <span className="tab-count">{fraudCount}</span>}
                  </button>
                ))}
              </div>
              <label className="search-field"><Search size={15} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search ID or card" aria-label="Search by transaction or card ID" /></label>
            </div>

            <div className="table-layout">
              <div className="table-scroll">
                <table>
                  <thead><tr><th>Transaction</th><th>Card</th><th>Amount</th><th>Decision</th><th>Fraud score</th><th>Received</th></tr></thead>
                  <tbody>
                    {visibleTransactions.map((transaction) => (
                      <tr key={transaction.transactionId} className={selectedId === transaction.transactionId ? "row-selected" : ""} onClick={() => setSelectedId(transaction.transactionId)} tabIndex={0} onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") setSelectedId(transaction.transactionId);
                      }}>
                        <td><span className="transaction-id">{transaction.transactionId}</span></td>
                        <td><span className="card-id"><Fingerprint size={13} /> {maskCardReference(transaction.cardId)}</span></td>
                        <td className="amount-cell">{formatMoney(transaction.amount)}</td>
                        <td><StatusBadge status={transaction.status} isFraud={transaction.isFraud} /></td>
                        <td>{transaction.fraudProbability === null ? <span className="muted-value">--</span> : (
                          <span className={`score-value${transaction.isFraud ? " score-high" : ""}`}>
                            {formatPercent(transaction.fraudProbability)}
                            <span className="score-track"><span style={{ width: `${Math.min(100, transaction.fraudProbability * 100)}%` }} /></span>
                          </span>
                        )}</td>
                        <td className="time-cell">{formatDate(transaction.createdAt)}</td>
                      </tr>
                    ))}
                    {!visibleTransactions.length && <tr><td className="empty-cell" colSpan={6}>{transactions.length ? "No transactions match these filters." : "No transactions yet. Submit a demo transaction to start the flow."}</td></tr>}
                  </tbody>
                </table>
              </div>
              <aside className="detail-panel" aria-label="Selected transaction details">
                {selectedTransaction ? <TransactionDetail transaction={selectedTransaction} /> : (
                  <div className="detail-empty">
                    <div className="detail-icon"><Fingerprint size={18} /></div>
                    <span className="section-kicker">RECORD INSPECTOR</span>
                    <h3>Select a transaction</h3>
                    <p>Its lifecycle, score, and timestamps will appear here.</p>
                  </div>
                )}
              </aside>
            </div>
            <div className="table-footer"><span>Showing {visibleTransactions.length} of {transactions.length} records</span><span><span className="live-dot" /> Polling gateway</span></div>
          </section>

          <section className="model-section" id="model-quality">
            <div className="section-heading model-heading">
              <div><div className="section-kicker">MODEL CARD · HOLDOUT EVALUATION</div><h2>Model quality</h2></div>
              <div className="model-chip"><span className="model-chip-dot" /> {metrics?.active_model.replaceAll("_", " ") ?? "Model"} · active</div>
            </div>
            {metrics ? (
              <>
                <div className="model-summary">
                  <div><span>Validation strategy</span><strong>{metrics.cross_validation.fold_count}-fold stratified CV</strong></div>
                  <div><span>Decision threshold</span><strong>0.50</strong></div>
                  <div><span>Evaluation split</span><strong>Untouched 20% holdout</strong></div>
                  <div><span>Metric emphasis</span><strong>Precision-recall</strong></div>
                </div>
                <div className="quality-table-wrap">
                  <table className="quality-table">
                    <thead><tr><th>Model</th><th>CV PR-AUC</th><th>CV precision</th><th>CV recall</th><th>Holdout PR-AUC</th><th>Holdout F1</th></tr></thead>
                    <tbody>{Object.entries(metrics.cross_validation.metrics).map(([name, values]) => {
                      const test = metrics.test_metrics_at_threshold_0_5[name];
                      return <tr key={name}>
                        <td className="model-name">{name.replaceAll("_", " ")}</td>
                        <td>{values.average_precision.mean.toFixed(3)} <span className="std-value">± {values.average_precision.std.toFixed(3)}</span></td>
                        <td>{values.precision_at_0_5.mean.toFixed(3)} <span className="std-value">± {values.precision_at_0_5.std.toFixed(3)}</span></td>
                        <td>{values.recall_at_0_5.mean.toFixed(3)} <span className="std-value">± {values.recall_at_0_5.std.toFixed(3)}</span></td>
                        <td>{test.average_precision.toFixed(3)}</td>
                        <td>{test.f1.toFixed(3)}</td>
                      </tr>;
                    })}</tbody>
                  </table>
                </div>
                <div className="model-footnote"><ShieldCheck size={15} /> The holdout is separate from cross-validation. Threshold tuning requires a new validation-only experiment.</div>
              </>
            ) : <div className="metrics-unavailable"><AlertTriangle size={17} /><span>Model metrics are unavailable. Check the inference service and its training report.</span></div>}
          </section>

          <footer className="page-footer"><span>Fraud Detection System</span><span>Local portfolio environment · Kafka remains private</span></footer>
        </div>
      </main>

      {isDialogOpen && (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsDialogOpen(false);
        }}>
          <section className="transaction-dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
            <div className="dialog-heading">
              <div><div className="section-kicker">NEW ANALYSIS</div><h2 id="dialog-title">Submit a demo transaction</h2></div>
              <button className="icon-button" onClick={() => setIsDialogOpen(false)} aria-label="Close dialog" type="button"><X size={18} /></button>
            </div>
            <p className="dialog-copy">This creates a synthetic 30-feature vector for the local demo. It is not a real card transaction.</p>
            <form onSubmit={(event) => void submitTransaction(event)}>
              <label className="form-label">Transaction ID <span>Optional</span><input value={transactionId} onChange={(event) => setTransactionId(event.target.value)} placeholder="Generated if blank" maxLength={100} /></label>
              <label className="form-label">Card reference<input value={cardId} onChange={(event) => setCardId(event.target.value)} placeholder="e.g. card-demo-01" required maxLength={100} /></label>
              <label className="form-label">Amount (USD)<div className="currency-input"><span>$</span><input type="number" value={amount} onChange={(event) => setAmount(event.target.value)} min="0" step="0.01" required /></div></label>
              <div className="synthetic-note"><CircleHelp size={15} /><span>The other 29 model inputs are neutral demo values. Use real, correctly ordered features only in a controlled test.</span></div>
              <div className="dialog-actions">
                <button className="quiet-button" onClick={() => setIsDialogOpen(false)} type="button">Cancel</button>
                <button className="primary-button" disabled={isSubmitting} type="submit">{isSubmitting ? <RefreshCw className="spin" size={16} /> : <Activity size={16} />}{isSubmitting ? "Submitting" : "Submit for analysis"}</button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

function StatusBadge({ status, isFraud }: { status: Status; isFraud: boolean | null }) {
  if (status === "COMPLETED" && isFraud) return <span className="status-badge fraud"><AlertTriangle size={13} /> Fraud flagged</span>;
  const icon = status === "COMPLETED" ? <Check size={13} /> : status === "FAILED" ? <X size={13} /> : <Clock3 size={13} />;
  return <span className={`status-badge ${status.toLowerCase()}`}>{icon}{status.toLowerCase()}</span>;
}

function TransactionDetail({ transaction }: { transaction: Transaction }) {
  return (
    <div className="detail-content">
      <div className="detail-topline"><span className="section-kicker">RECORD INSPECTOR</span><StatusBadge status={transaction.status} isFraud={transaction.isFraud} /></div>
      <h3 className="detail-id">{transaction.transactionId}</h3>
      <div className="detail-score-block">
        <span>Fraud probability</span>
        <strong className={transaction.isFraud ? "text-risk" : ""}>{formatPercent(transaction.fraudProbability)}</strong>
        {transaction.fraudProbability !== null && <div className="large-score-track"><span style={{ width: `${Math.min(100, transaction.fraudProbability * 100)}%` }} /></div>}
      </div>
      <dl className="detail-list">
        <div><dt>Card reference</dt><dd>{maskCardReference(transaction.cardId)}</dd></div>
        <div><dt>Amount</dt><dd>{formatMoney(transaction.amount)}</dd></div>
        <div><dt>Transaction time</dt><dd>{formatDate(transaction.timestamp)}</dd></div>
        <div><dt>Received</dt><dd>{formatDate(transaction.createdAt)}</dd></div>
        <div><dt>Last status update</dt><dd>{formatDate(transaction.updatedAt)}</dd></div>
      </dl>
      {transaction.message && <div className="failure-message"><AlertTriangle size={14} />{transaction.message}</div>}
      <div className="lifecycle">
        <span className="section-kicker">LIFECYCLE</span>
        <div className={`lifecycle-step${transaction.status !== "FAILED" ? " reached" : ""}`}><span className="step-mark"><Check size={11} /></span><span>Queued</span></div>
        <div className={`lifecycle-step${transaction.status === "PROCESSING" || transaction.status === "COMPLETED" ? " reached" : ""}`}><span className="step-mark"><Activity size={11} /></span><span>{transaction.status === "PROCESSING" ? "Processing" : "Analysis"}</span></div>
        <div className={`lifecycle-step${transaction.status === "COMPLETED" || transaction.status === "FAILED" ? " reached" : ""}`}><span className="step-mark"><Check size={11} /></span><span>{transaction.status === "FAILED" ? "Failed" : "Result stored"}</span></div>
      </div>
    </div>
  );
}

export default App;