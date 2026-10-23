package edu.registration.finalization;

/** Every failure raised by subsystem C. */
public class FinalizationException extends RuntimeException {
    public enum Reason { GATEWAY_UNAVAILABLE, UNKNOWN_COURSE, NOT_FOUND, ILLEGAL_TRANSITION }

    private final Reason reason;

    public FinalizationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
