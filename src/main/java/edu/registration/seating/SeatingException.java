package edu.registration.seating;

 
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

     
    public int waitlistPosition() { return waitlistPosition; }
}
