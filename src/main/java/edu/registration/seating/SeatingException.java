package edu.registration.seating;

/** Every failure raised by subsystem B. For SECTION_FULL the waitlist position is filled in. */
public class SeatingException extends RuntimeException {
    public enum Reason { SECTION_FULL, UNKNOWN_COURSE, SEAT_NOT_FOUND, ILLEGAL_TRANSITION }

    private final Reason reason;
    private final int waitlistPosition;

    public SeatingException(Reason reason, String message) {
        this(reason, message, 0);
    }

    public SeatingException(Reason reason, String message, int waitlistPosition) {
        super(message);
        this.reason = reason;
        this.waitlistPosition = waitlistPosition;
    }

    public Reason reason() { return reason; }

    /** 1-based position on the waitlist, or 0 if the failure has nothing to do with the waitlist. */
    public int waitlistPosition() { return waitlistPosition; }
}
