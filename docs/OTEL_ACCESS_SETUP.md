# Give the OTel workflows access

Use the existing GitHub Workload Identity Federation connection. No password, personal login token or service-account JSON key needs to be shared.

## Simplest setup for this POC

1. Open [IAM for the POC project](https://console.cloud.google.com/iam-admin/iam?project=project-c9bd3d0e-266f-47bf-852).
2. Find `github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com`, the repository's default workflow account. If your `gcp-poc` environment's `GCP_SERVICE_ACCOUNT` secret points to a different account, edit that account instead.
3. Click the pencil/edit button, then **Add another role** for each role below. Add them on this project; keep the existing deployment roles.

| Role shown in Console | Role ID | Why |
| --- | --- | --- |
| Logs Viewer | roles/logging.viewer | Read stored application logs |
| Cloud Trace User | roles/cloudtrace.user | Read stored distributed traces |
| Monitoring Viewer | roles/monitoring.viewer | Query stored metrics |
| Monitoring Dashboard Configuration Editor | roles/monitoring.dashboardEditor | Create/update the POC dashboards |

4. Save. If you cannot edit IAM, ask your project administrator to grant these four project-scoped roles to that service account. Adding roles to your own user alone does not change the workflow identity's access.
5. In GitHub Actions, manually run **OTel access check** on `main`. Its artifact identifies the selected account and lists granted/missing permissions. Allow time for IAM propagation before rerunning a check after a grant.
6. Once access is ready, run **OTel Cloud dashboards** with `apply=true` and `verify_queries=true`, then **manual-cloud-acceptance**. These workflows now check the required access first. Missing access stops the acceptance workflow before it creates a sandbox payment.
7. Sign into the frontend as support/admin. Open **Observability > Recent API request traces**. Your own Google Cloud user also needs the three viewing roles to open the linked logs, traces and metrics; it does not need dashboard-edit permission just to view them.

There is no change to Cloud Run public/private invoker IAM. These workflows never grant roles themselves. Application runtime accounts do not need these extra observer roles.

## Optional administrator command reference

From the repository, print the four commands for your administrator to review:

```cmd
python scripts\cloud\otel_access.py --project project-c9bd3d0e-266f-47bf-852 --principal github-deployer@project-c9bd3d0e-266f-47bf-852.iam.gserviceaccount.com --print-admin-commands
```

This only prints commands; it does not execute them. The administrator must run the reviewed commands using an identity allowed to manage project IAM.

## Optional separate observability identity

IT can instead use a dedicated service account with the four roles above and Cloud Run read access (for example `roles/run.viewer`) for service URL discovery. In GitHub **Settings > Environments > gcp-poc**, set secret `GCP_OBSERVABILITY_SERVICE_ACCOUNT` to that account's email. Observability workflows prefer this secret and otherwise use `GCP_SERVICE_ACCOUNT`; deployment workflows continue using the existing deployer.

IT must also allow the existing GitHub WIF principal to impersonate the dedicated account using the repository's existing trust restrictions. Setting the email alone is not an impersonation grant. Reuse the current provider; no downloaded account key is needed. Keep project/service conditions and repository/ref restrictions in the existing trust design.

## What the access check establishes

`otel_access.py` calls Resource Manager `projects.testIamPermissions` for the current WIF identity. It reports only required granted/missing permission names and the account/project. It does not read raw log payloads, create payments, edit dashboards, deploy services or change IAM. The standalone check requires all observer permissions; individual workflows check only their scope. Rendering dashboard definitions without querying or publishing still requires no observer permission check.

This is a conservative project-level permission check. Custom resource-conditional grants may require administrator review. A successful preflight does not prove API enablement, service-perimeter access, telemetry ingestion, stored data or dashboard correctness. Those require the separate live workflows. A denied permission test is reported as blocked, never as a pass.

## Additional features and activation

The repository now includes eleven dashboards, wallet/request spans and metrics, bounded production browser telemetry, manual warmup, alert policy generation, and collector queue/export metrics. Real local drills cover alert firing/recovery delivery and persistent-queue process restart. Follow [the feature and activation guide](OTEL_REMAINING_FEATURES.md) for release and verification commands.

After each release, verify stored signals and every dashboard query. A passing business request or returned trace ID alone does not prove storage. Wallet attempts count retries; committed database snapshots supply unique deposit/request counts.

Cloud alert publication additionally requires an administrator to grant `roles/monitoring.alertPolicyEditor` to the selected workflow account and supply an existing enabled/verified notification channel. The four baseline roles above do not grant policy publication. Local delivery tests do not prove delivery through that cloud channel.

Warm minimum instances and an external persistent collector are opt-in infrastructure choices. No new always-on services or external queue host are enabled by the access setup. Cloud Run ephemeral storage does not provide durable queues across instance replacement.

References: [Monitoring access and dashboard roles](https://docs.cloud.google.com/monitoring/access-control), [Cloud Trace access](https://docs.cloud.google.com/trace/docs/iam), [permission testing](https://docs.cloud.google.com/resource-manager/reference/rest/v3/projects/testIamPermissions).
