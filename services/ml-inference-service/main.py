import json
import os
from math import isfinite
from pathlib import Path

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="Fraud Detection Inference API", version="1.0.0")

BASE_DIR = Path(__file__).resolve().parent
REPOSITORY_MODELS_DIR = BASE_DIR.parents[1] / "models"
MODEL_FILES = {
    "xgboost": REPOSITORY_MODELS_DIR / "xgboost_fraud_model_cv.joblib",
    "logistic_regression": REPOSITORY_MODELS_DIR / "logistic_regression_fraud_model_cv.joblib",
}
MODEL_NAME = os.getenv("FRAUD_MODEL", "xgboost").lower()
MODEL_PATH = Path(os.getenv("FRAUD_MODEL_PATH", MODEL_FILES.get(MODEL_NAME, "")))
METRICS_PATH = REPOSITORY_MODELS_DIR / "fraud_models_cv.metrics.json"

try:
    model = joblib.load(MODEL_PATH) if MODEL_PATH.is_file() else None
except Exception as error:
    print(f"Could not load fraud model at {MODEL_PATH}: {error}")
    model = None


class PredictionRequest(BaseModel):
    features: list[float]


@app.get("/health")
def health_check():
    if model is None:
        raise HTTPException(status_code=503, detail="Fraud model is not loaded.")
    return {"status": "UP", "model_loaded": True, "model": MODEL_NAME}


@app.get("/metrics")
def model_metrics():
    if model is None:
        raise HTTPException(status_code=503, detail="Fraud model is not loaded.")
    if not METRICS_PATH.is_file():
        raise HTTPException(status_code=404, detail="Model evaluation report is not available.")
    try:
        report = json.loads(METRICS_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise HTTPException(status_code=500, detail="Could not read model evaluation report.") from error
    return {**report, "active_model": MODEL_NAME}


@app.post("/predict")
def predict(data: PredictionRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Fraud model is not loaded.")

    if not data.features or not all(isfinite(value) for value in data.features):
        raise HTTPException(status_code=422, detail="features must contain finite numeric values.")

    expected_features = getattr(model, "n_features_in_", None)
    if expected_features is not None and len(data.features) != expected_features:
        raise HTTPException(
            status_code=422,
            detail=f"Expected {expected_features} features, received {len(data.features)}.",
        )

    try:
        feature_row = np.asarray(data.features, dtype=float).reshape(1, -1)
        prediction = int(model.predict(feature_row)[0])
        probabilities = model.predict_proba(feature_row)[0]
        classes = list(model.classes_)
        fraud_class_index = classes.index(1)
    except (AttributeError, ValueError, IndexError, TypeError) as error:
        raise HTTPException(status_code=500, detail="Loaded model has an incompatible prediction interface.") from error

    return {
        "isFraud": prediction == 1,
        "fraudProbability": float(probabilities[fraud_class_index]),
    }