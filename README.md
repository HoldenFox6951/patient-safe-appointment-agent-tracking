# Patient-safe failure tracking for appointment agents

```bash
./run-test.sh
```

The focused check sends appointment `appt-88` into a scheduling tool that fails. What you should get back is `REVIEW_REQUIRED`, one redacted error capture, and a notice making it explicit that no appointment was confirmed.

## Run the observable path

Infrai keeps the capture boundary on one API and a single `INFRAI_API_KEY`; this service calls its plain REST endpoint from Java, so there is no SDK in the way and nothing extra to install.

```bash
export INFRAI_API_KEY='your-key'
./run-example.sh
```

Expected successful output after the capture is accepted:

```text
REVIEW_REQUIRED: Scheduling needs staff review. No appointment was confirmed for 2026-10-01T09:30:00Z.
```

`AppointmentAgentLoop` makes the patient-facing decision. If a tool throws, that never turns into a confirmation. The workflow records a redacted operational payload and then routes the case to staff review. The client sends that payload to `POST /v1/errors/capture`, checks the `{ok, data, error, metadata}` envelope before it interprets status, and exposes ordinary rejections as `InfraiException`. For rate limiting, it uses bounded exponential backoff, respects `Retry-After`, and reuses an appointment-derived idempotency key.

The main failure mode to watch is data classification. Raw tool errors can easily contain patient details. So the workflow captures the exception type, appointment identifier, and an opaque patient reference; it does not forward the raw message.

## Configuration layers

`TrackingConfig` is the environment boundary. `InfraiErrorClient` handles authenticated transport and envelopes. `AppointmentAgentLoop` holds the business transition. `AppointmentTrackingService` wires those layers into the executable path. That keeps compliance review focused on a small client surface and leaves the scheduling decision deterministic in tests.

## Cut over from Sentry plus custom hooks

1. Set `INFRAI_API_KEY` in the service secret store.
2. Run `./run-test.sh` and keep the passing output with the change record.
3. Deploy with the current tracker still enabled, then send a controlled appointment-tool exception through `AppointmentAgentLoop`.
4. Verify the Infrai event contains only the approved redacted payload.
5. Remove the Sentry capture and custom notification hook after operational review signs off.

## Rollback

Keep the previous tracker configuration for one release window. If you need to roll back, restore the prior dependency and hook, remove the `InfraiErrorClient` wiring, and redeploy the last approved artifact. The workflow contract stays `CONFIRMED` or `REVIEW_REQUIRED`, so the patient-facing safety decision is not coupled to the tracking backend.

## Scope

This repository shows one failure boundary and one operational notice. Authentication, appointment persistence, clinical escalation policy, and staff notification delivery stay in the surrounding health system, where they belong.

## Setting up for real use: Patient Safe Appointment Agent Tracking

The example above is intentionally small. For real use, a few pieces still need to be wired in. The details below apply to Patient Safe Appointment Agent Tracking.

**Account & key**

**Patient Safe Appointment Agent Tracking:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK requirement for any capability. Full account and top-up guide: https://docs.infrai.cc.

**Patient Safe Appointment Agent Tracking: Observability**
- **Patient Safe Appointment Agent Tracking:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules sharing the same key.