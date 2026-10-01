package dev.clinic.agent.workflow;

import java.util.ArrayList;
import java.util.List;

public final class AppointmentAgentLoopTest {
    public static void main(String[] args) {
        List<String> captures = new ArrayList<>();
        AppointmentAgentLoop loop = new AppointmentAgentLoop((payload, key) -> captures.add(payload + "|" + key));
        AppointmentAgentLoop.Result result = loop.schedule(
                new AppointmentAgentLoop.Request("appt-88", "patient-safe-12", "2026-10-03T08:00:00Z"),
                () -> { throw new IllegalArgumentException("slot unavailable"); });

        check(result.outcome() == AppointmentAgentLoop.Outcome.REVIEW_REQUIRED, "failure must require staff review");
        check(result.operationalNotice().contains("No appointment was confirmed"), "notice must not imply success");
        check(captures.size() == 1, "one failure must create one capture");
        check(captures.get(0).contains("appointment-appt-88"), "capture must use the stable appointment key");
        check(!captures.get(0).contains("slot unavailable"), "capture must exclude raw tool detail");
        System.out.println("PASS: failed scheduling routes to review and emits one redacted capture");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
