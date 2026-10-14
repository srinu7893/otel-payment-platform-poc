# Remaining Cloud Run bootstrap commands — do not execute during review

Run these only manually, from the repository root in an authenticated Cloud Shell, after choosing existing application digests and confirming secrets/IAM. The generation commands only write local manifests and print shell commands. They do not contact GCP. `config.bootstrap.json` is your populated copy of `config.bootstrap.example.json`; confirm the supplied CloudAMQP host/vhost and every referenced secret/version exists. Each image variable below must contain its full immutable registry digest, not a tag. The Collector digest comes from the GitHub `COLLECTOR_IMAGE` variable. No customer bootstrap is needed.

## One-time IAM grants, if not already present

```sh
gcloud iam service-accounts add-iam-policy-binding otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/iam.serviceAccountUser
gcloud iam service-accounts add-iam-policy-binding otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/iam.serviceAccountTokenCreator
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/cloudtrace.agent
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/logging.logWriter
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/monitoring.metricWriter
gcloud artifacts repositories add-iam-policy-binding otel-payment-platform --project=project-c9bd3d0e-266f-47bf-852 --location=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.admin
```

Confirm the existing WIF subject grant on github-deployer separately; the pool/provider subject is environment-specific and must retain its existing restricted binding. No guessed provider or broad principalSet command is generated.

Grant only used secrets (adjust names to your actual populated configuration):

```sh
gcloud secrets add-iam-policy-binding neon-jdbc-url --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding neon-username --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding neon-password --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding jwt-secret --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding rabbit-username --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding rabbit-password --project=project-c9bd3d0e-266f-47bf-852 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

## mock-bank-service

```sh
python3 scripts/cloud/bootstrap_service.py commands --service mock-bank-service --config deploy/cloud-run/config.bootstrap.json --image "$BANK_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/mock-bank-service.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding mock-bank-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services add-iam-policy-binding mock-bank-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services describe mock-bank-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service mock-bank-service --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## gateway-service

```sh
python3 scripts/cloud/bootstrap_service.py commands --service gateway-service --config deploy/cloud-run/config.bootstrap.json --image "$GATEWAY_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/gateway-service.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding gateway-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services add-iam-policy-binding gateway-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services describe gateway-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service gateway-service --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## payment-service

```sh
python3 scripts/cloud/bootstrap_service.py commands --service payment-service --config deploy/cloud-run/config.bootstrap.json --image "$PAYMENT_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/payment-service.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding payment-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services add-iam-policy-binding payment-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services describe payment-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service payment-service --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## notification-service

```sh
python3 scripts/cloud/bootstrap_service.py commands --service notification-service --config deploy/cloud-run/config.bootstrap.json --image "$NOTIFICATION_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/notification-service.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding notification-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services add-iam-policy-binding notification-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services describe notification-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service notification-service --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## auth-service

```sh
python3 scripts/cloud/bootstrap_service.py commands --service auth-service --config deploy/cloud-run/config.bootstrap.json --image "$AUTH_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/auth-service.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding auth-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:otel-payment-runtime@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services add-iam-policy-binding auth-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=serviceAccount:github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --role=roles/run.invoker
gcloud run services describe auth-service --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service auth-service --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## api-gateway

```sh
python3 scripts/cloud/bootstrap_service.py commands --service api-gateway --config deploy/cloud-run/config.bootstrap.json --image "$API_GATEWAY_IMAGE" --collector "$COLLECTOR_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/api-gateway.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding api-gateway --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=allUsers --role=roles/run.invoker
gcloud run services describe api-gateway --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service api-gateway --config deploy/cloud-run/config.bootstrap.json --collector "$COLLECTOR_IMAGE"
```

## payment-frontend

```sh
python3 scripts/cloud/bootstrap_service.py commands --service frontend --config deploy/cloud-run/config.bootstrap.json --image "$FRONTEND_IMAGE"
# Review generated manifest before manually running the following:
gcloud run services replace artifacts/bootstrap/frontend.json --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1
gcloud run services add-iam-policy-binding payment-frontend --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --member=allUsers --role=roles/run.invoker
gcloud run services describe payment-frontend --project=project-c9bd3d0e-266f-47bf-852 --region=us-central1 --format="value(status.url)"
python3 scripts/cloud/bootstrap_service.py check --service frontend --config deploy/cloud-run/config.bootstrap.json
```

These manifests retain all OTel exporters. A dependency-free bootstrap slot is only startup infrastructure; GitHub later discovers dependency URLs and enables workload IAM for the final application. Workers can execute scheduled/listener work immediately after bootstrap; review DB/broker state before manually starting them. The shared account means its private self-Invoker grants also cover the configured internal caller graph.

## Stable configuration after bootstrap

The expected runtime is selected from an optional per-service override, then `defaultServiceAccount`. Every existing service must match that expected identity. No `urls` or `serviceAccounts` object is required. GitHub discovers the canonical origin and live properties itself; never add a new URL/account mapping merely because another service was bootstrapped. Use the same stable JSON in `config.example.json` for all eight workflows.
