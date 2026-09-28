import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
import xgboost
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    make_scorer,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import StratifiedKFold, cross_validate, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier


ROOT_DIR = Path(__file__).resolve().parents[2]
TARGET_COLUMN = "Class"
EXPECTED_FEATURE_COUNT = 30


def parse_args():
    parser = argparse.ArgumentParser(description="Cross-validate and retrain fraud detection models.")
    parser.add_argument("--data", type=Path, default=ROOT_DIR / "creditcard.csv")
    parser.add_argument("--models-dir", type=Path, default=ROOT_DIR / "models")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--n-estimators", type=int, default=220)
    parser.add_argument("--n-jobs", type=int, default=4)
    parser.add_argument("--cv-folds", type=int, default=5)
    return parser.parse_args()


def build_models(seed: int, n_estimators: int, n_jobs: int):
    logistic_regression = Pipeline(
        steps=[
            ("scaler", ColumnTransformer(
                transformers=[("time_amount", StandardScaler(), [0, 29])],
                remainder="passthrough",
            )),
            ("classifier", LogisticRegression(solver="liblinear", max_iter=1000, random_state=seed)),
        ]
    )
    xgboost_model = XGBClassifier(
        objective="binary:logistic",
        eval_metric="aucpr",
        n_estimators=n_estimators,
        learning_rate=0.05,
        max_depth=4,
        min_child_weight=1,
        subsample=0.9,
        colsample_bytree=0.9,
        scale_pos_weight=1.0,
        tree_method="hist",
        n_jobs=n_jobs,
        random_state=seed,
    )
    return {
        "logistic_regression": logistic_regression,
        "xgboost": xgboost_model,
    }


def calculate_metrics(labels, probabilities, predictions):
    return {
        "average_precision": float(average_precision_score(labels, probabilities)),
        "roc_auc": float(roc_auc_score(labels, probabilities)),
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "f1": float(f1_score(labels, predictions, zero_division=0)),
        "confusion_matrix_labels_0_1": confusion_matrix(labels, predictions, labels=[0, 1]).tolist(),
    }


def summarize_cv(estimator, features, labels, folds):
    scorers = {
        "average_precision": "average_precision",
        "roc_auc": "roc_auc",
        "precision_at_0_5": make_scorer(precision_score, zero_division=0),
        "recall_at_0_5": make_scorer(recall_score, zero_division=0),
        "f1_at_0_5": make_scorer(f1_score, zero_division=0),
    }
    scores = cross_validate(
        estimator,
        features,
        labels,
        cv=folds,
        scoring=scorers,
        n_jobs=1,
        return_train_score=False,
        error_score="raise",
    )
    return {
        metric: {
            "mean": float(np.mean(scores[f"test_{metric}"])),
            "std": float(np.std(scores[f"test_{metric}"])),
            "folds": [float(value) for value in scores[f"test_{metric}"]],
        }
        for metric in scorers
    }


def main():
    args = parse_args()
    if not args.data.is_file():
        raise FileNotFoundError(f"Training dataset not found: {args.data}")

    dataset = pd.read_csv(args.data)
    if TARGET_COLUMN not in dataset.columns:
        raise ValueError(f"Dataset must contain the target column '{TARGET_COLUMN}'.")

    feature_columns = [column for column in dataset.columns if column != TARGET_COLUMN]
    if len(feature_columns) != EXPECTED_FEATURE_COUNT:
        raise ValueError(f"Expected {EXPECTED_FEATURE_COUNT} features, found {len(feature_columns)}.")

    features = dataset[feature_columns].to_numpy(dtype=np.float32)
    labels = dataset[TARGET_COLUMN].to_numpy(dtype=np.int32)
    if not np.isfinite(features).all():
        raise ValueError("Feature data contains missing or non-finite values.")
    if set(np.unique(labels)) != {0, 1}:
        raise ValueError("Target column must contain both binary classes 0 and 1.")

    development_x, test_x, development_y, test_y = train_test_split(
        features,
        labels,
        test_size=0.2,
        random_state=args.seed,
        stratify=labels,
    )
    if args.cv_folds < 2:
        raise ValueError("At least two cross-validation folds are required.")

    folds = StratifiedKFold(n_splits=args.cv_folds, shuffle=True, random_state=args.seed)
    models = build_models(args.seed, args.n_estimators, args.n_jobs)
    cv_results = {}
    test_results = {}

    args.models_dir.mkdir(parents=True, exist_ok=True)
    for name, estimator in models.items():
        cv_results[name] = summarize_cv(estimator, development_x, development_y, folds)
        estimator.fit(development_x, development_y)
        probabilities = estimator.predict_proba(test_x)[:, 1]
        predictions = (probabilities >= 0.5).astype(np.int32)
        test_results[name] = calculate_metrics(test_y, probabilities, predictions)
        joblib.dump(estimator, args.models_dir / f"{name}_fraud_model_cv.joblib")

    report = {
        "dataset_rows": int(len(dataset)),
        "feature_count": len(feature_columns),
        "feature_order": feature_columns,
        "class_counts": {str(label): int(count) for label, count in zip(*np.unique(labels, return_counts=True))},
        "split_rows": {"development": int(len(development_y)), "test": int(len(test_y))},
        "cross_validation": {
            "strategy": "stratified_k_fold",
            "fold_count": args.cv_folds,
            "development_rows_per_fold": int(len(development_y) / args.cv_folds),
            "seed": args.seed,
            "threshold": 0.5,
            "metrics": cv_results,
        },
        "test_metrics_at_threshold_0_5": test_results,
        "xgboost_n_estimators": args.n_estimators,
        "seed": args.seed,
        "versions": {
            "python": __import__("platform").python_version(),
            "numpy": np.__version__,
            "pandas": pd.__version__,
            "scikit_learn": sklearn.__version__,
            "xgboost": xgboost.__version__,
        },
    }

    report_path = args.models_dir / "fraud_models_cv.metrics.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    for name in models:
        print(f"Model saved to: {args.models_dir / f'{name}_fraud_model_cv.joblib'}")
    print(f"Metrics saved to: {report_path}")


if __name__ == "__main__":
    main()