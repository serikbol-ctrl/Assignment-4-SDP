package edu.registration.eligibility;

/** Every failure raised by subsystem A. The reason tells callers what went wrong without parsing text. */
public class EligibilityException extends RuntimeException {
    public enum Reason {
        UNKNOWN_STUDENT, UNKNOWN_COURSE, PREREQUISITE_NOT_MET, FINANCIAL_HOLD, HOLD_NOT_FOUND, ILLEGAL_TRANSITION
    }

    private final Reason reason;

    public EligibilityException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
