package dev.clinic.agent.workflow;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AppointmentAgentLoop {
    public enum Outcome { CONFIRMED, REVIEW_REQUIRED }
    public record Request(String appointmentId, String patientReference, String requestedSlot) {}
    public record Result(Outcome outcome, String operationalNotice) {}

    private final BiConsumer<String, String> failureCapture;

    public AppointmentAgentLoop(BiConsumer<String, String> failureCapture) {
        this.failureCapture = Objects.requireNonNull(failureCapture);
    }

    public Result schedule(Request request, Supplier<String> schedulingTool) {
        Objects.requireNonNull(request);
        try {
            String confirmation = schedulingTool.get();
            return new Result(Outcome.CONFIRMED, "Appointment confirmed: " + confirmation);
        } catch (RuntimeException failure) {
            String auditSafeException = failure.getClass().getSimpleName()
                    + " during appointment " + request.appointmentId()
                    + " for patient reference " + request.patientReference();
            failureCapture.accept(auditSafeException, "appointment-" + request.appointmentId());
            return new Result(Outcome.REVIEW_REQUIRED,
                    "Scheduling needs staff review. No appointment was confirmed for " + request.requestedSlot() + ".");
        }
    }
}
