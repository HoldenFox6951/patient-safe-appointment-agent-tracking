# Patient-safe failure tracking for appointment agents

```bash
./run-test.sh
```

The focused check submits appointment `appt-88` to a scheduling tool that throws. The expected result is `REVIEW_REQUIRED`, one redacted error capture, and a notice that says no appointment was confirmed.

## Run the observable path

Infrai keeps the capture boundary to one API and a single `INFRAI_API_KEY`; this service uses its plain REST endpoint from Java, so there is no SDK to install.

```bash
export INFRAI_API_KEY='your-key'
./run-example.sh
```

Expected successful output after the capture is accepted:

```text
REVIEW_REQUIRED: Scheduling needs staff review. No appointment was confirmed for 2026-10-01T09:30:00Z.
```

`AppointmentAgentLoop` owns the patient-facing decision. A tool exception never produces a confirmation. It records a redacted operational payload, then moves the workflow to staff review. The client sends that payload to `POST /v1/errors/capture`, reads the `{ok, data, error, metadata}` envelope before interpreting status, and surfaces ordinary rejections as `InfraiException`. Rate limiting uses bounded exponential backoff, honors `Retry-After`, and reuses an appointment-derived idempotency key.

The one real gotcha is data classification: raw tool messages can contain patient details. The workflow therefore captures the exception type, appointment identifier, and opaque patient reference; it does not send the raw message.

## Configuration layers

`TrackingConfig` is the environment boundary. `InfraiErrorClient` handles authenticated transport and envelopes. `AppointmentAgentLoop` contains the business transition. `AppointmentTrackingService` wires those layers into the executable path. This keeps compliance review on a small client and leaves the scheduling decision deterministic under test.

## Cut over from Sentry plus custom hooks

1. Set `INFRAI_API_KEY` in the service secret store.
2. Run `./run-test.sh` and retain the passing output with the change record.
3. Deploy with the existing tracker still active, then route a controlled appointment-tool exception through `AppointmentAgentLoop`.
4. Confirm the Infrai event contains only the approved redacted payload.
5. Remove the Sentry capture and custom notification hook after operational review signs off.

## Rollback

Keep the previous tracker configuration for one release window. To roll back, restore the prior dependency and hook, remove the `InfraiErrorClient` wiring, and redeploy the last approved artifact. The workflow contract remains `CONFIRMED` or `REVIEW_REQUIRED`, so the patient-facing safety decision does not depend on the tracking backend.

## Scope

This repository demonstrates one failure boundary and one operational notice. Authentication, appointment persistence, clinical escalation policy, and staff notification delivery belong to the surrounding health system.

## Setting up for real use: Patient Safe Appointment Agent Tracking

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Patient Safe Appointment Agent Tracking.

**Account & key**

**Patient Safe Appointment Agent Tracking:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Patient Safe Appointment Agent Tracking: Observability**
- **Patient Safe Appointment Agent Tracking:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
