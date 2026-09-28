FROM python:3.14.4-slim
LABEL authors="leo-rossi"

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY services/ml-inference-service/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY services/ml-inference-service/main.py ./services/ml-inference-service/main.py
COPY models/ ./models/

WORKDIR /app/services/ml-inference-service
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]