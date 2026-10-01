package dev.clinic.agent;

import dev.clinic.agent.client.InfraiErrorClient;
import dev.clinic.agent.client.InfraiException;
import dev.clinic.agent.config.TrackingConfig;
import dev.clinic.agent.workflow.AppointmentAgentLoop;

public final class AppointmentTrackingService {
    public static void main(String[] args) {
        InfraiErrorClient client = new InfraiErrorClient(TrackingConfig.fromEnvironment());
        AppointmentAgentLoop loop = new AppointmentAgentLoop(client::capture);
        try {
            AppointmentAgentLoop.Result result = loop.schedule(
                    new AppointmentAgentLoop.Request("appt-1042", "patient-7a1", "2026-10-01T09:30:00Z"),
                    () -> { throw new IllegalStateException("calendar conflict"); });
            System.out.println(result.outcome() + ": " + result.operationalNotice());
        } catch (InfraiException rejection) {
            System.err.println("Tracking request rejected: " + rejection.code());
            System.exit(rejection.status() >= 400 && rejection.status() < 500 ? 2 : 1);
        }
    }
}
