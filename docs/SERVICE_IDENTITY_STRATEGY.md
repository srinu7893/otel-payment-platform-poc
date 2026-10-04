# Service-to-Service Identity Strategy

This document freezes the service identity boundary before OpenTelemetry. It separates end-user authorization from workload/service authentication so the local POC does not invent a weak shared-key mechanism simply to look production-like.

## Local Docker baseline

- The browser uses only API Gateway.
- The user JWT is validated at the edge and on secured resource services where enabled.
- Payment Service calls Customer Service, Gateway Service and other internal dependencies over the private Docker network.
- Mock Bank, PostgreSQL and RabbitMQ are internal implementation services.
- No internal service should be treated as safe to expose publicly simply because it has a host port in local Compose.

The local topology therefore uses trusted internal ingress for selected backend calls. This is a conscious POC boundary, not an omitted production control.

## GCP deployment direction

The preferred cloud design is:

1. only the edge/API entry point is internet-facing;
2. backend services use private ingress;
3. backend-to-backend calls authenticate with workload/service identity;
4. end-user identity remains distinct from service identity;
5. secrets that cannot be replaced by platform identity are stored in Secret Manager;
6. TLS is used on network paths according to the selected runtime/platform.

Conceptually:

Browser + user JWT
  -> API Gateway
  -> Payment Service (authorizes the user/business action)
  -> Customer Service (authenticates Payment Service workload)
  -> Gateway Service (authenticates Payment Service workload)
  -> Bank simulator (authenticates Gateway workload)

A downstream service should be able to distinguish:

- which workload/service is calling;
- which customer/business subject the authenticated workload is acting for.

The second value may be carried as trusted request context only after the caller workload has authenticated.

## Runtime mapping

### Cloud Run

Preferred when the services fit Cloud Run:
- private/internal ingress for backend services;
- dedicated service accounts;
- authenticated service-to-service invocation using Google-issued identity tokens/IAM;
- only the intended edge service is public.

### GKE

If Kubernetes is selected:
- private ClusterIP services;
- Kubernetes service accounts mapped with Workload Identity;
- NetworkPolicy;
- optional mTLS/service mesh if required by the environment.

### VM

Least preferred for this POC:
- private VPC/firewall controls;
- explicit service credential or mTLS mechanism;
- tighter manual secret/certificate lifecycle.

## What not to do

- do not reuse customer passwords for service authentication;
- do not forward bearer tokens to unrelated internal services by default;
- do not commit permanent service API keys;
- do not put service credentials in telemetry attributes or logs;
- do not treat network reachability alone as sufficient in a production deployment.

## OpenTelemetry implications

The OTel branch must propagate trace context, not credentials. Safe resource/span attributes include service.name, deployment environment, cloud runtime metadata, correlation ID and business operation IDs. Authorization headers, JWTs, secrets and full account values must be excluded.

## Baseline decision

For pre-OTel local/Docker work, the existing internal trust boundary is accepted and documented. Actual workload identity enforcement is a GCP deployment task because the mechanism depends on the manager-approved runtime.
