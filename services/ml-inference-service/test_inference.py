import numpy as np
from fastapi.testclient import TestClient

import main


class FakeModel:
    n_features_in_ = 3
    classes_ = np.array([0, 1])

    def predict(self, features):
        return np.array([1])

    def predict_proba(self, features):
        return np.array([[0.12, 0.88]])


client = TestClient(main.app)


def test_health_reports_loaded_model(monkeypatch):
    monkeypatch.setattr(main, "model", FakeModel())

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["model_loaded"] is True


def test_predict_returns_fraud_class_and_probability(monkeypatch):
    monkeypatch.setattr(main, "model", FakeModel())

    response = client.post("/predict", json={"features": [0.1, 0.2, 0.3]})

    assert response.status_code == 200
    assert response.json() == {"isFraud": True, "fraudProbability": 0.88}


def test_predict_rejects_wrong_feature_count(monkeypatch):
    monkeypatch.setattr(main, "model", FakeModel())

    response = client.post("/predict", json={"features": [0.1, 0.2]})

    assert response.status_code == 422


def test_predict_reports_unavailable_model(monkeypatch):
    monkeypatch.setattr(main, "model", None)

    response = client.post("/predict", json={"features": [0.1, 0.2, 0.3]})

    assert response.status_code == 503


def test_metrics_returns_evaluation_report(monkeypatch, tmp_path):
    report_path = tmp_path / "metrics.json"
    report_path.write_text('{"cross_validation": {"fold_count": 5}}')
    monkeypatch.setattr(main, "model", FakeModel())
    monkeypatch.setattr(main, "METRICS_PATH", report_path)

    response = client.get("/metrics")

    assert response.status_code == 200
    assert response.json()["active_model"] == main.MODEL_NAME
    assert response.json()["cross_validation"]["fold_count"] == 5


def test_metrics_reports_missing_model_report(monkeypatch, tmp_path):
    monkeypatch.setattr(main, "model", FakeModel())
    monkeypatch.setattr(main, "METRICS_PATH", tmp_path / "missing.json")

    response = client.get("/metrics")

    assert response.status_code == 404