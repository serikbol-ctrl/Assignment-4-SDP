package edu.registration.seating;

import edu.registration.seating.SeatingException.Reason;

/**
 * Subsystem B's entity: one student's seat in one course section.
 * RESERVED -> CONFIRMED -> RELEASED (or RESERVED -> RELEASED). RELEASED is final.
 */
public final class SeatAssignment {
    public enum Status { RESERVED, CONFIRMED, RELEASED }

    private final String id;
    private final String studentId;
    private final String courseId;
    private Status status;

    public SeatAssignment(String id, String studentId, String courseId, Status status) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = status;
    }

    public static SeatAssignment reserved(String id, String studentId, String courseId) {
        return new SeatAssignment(id, studentId, courseId, Status.RESERVED);
    }

    public String id() { return id; }
    public String studentId() { return studentId; }
    public String courseId() { return courseId; }
    public Status status() { return status; }

    /** A seat that still occupies room in the section. */
    public boolean isActive() {
        return status != Status.RELEASED;
    }

    public void confirm() {
        moveTo(Status.CONFIRMED);
    }

    public void release() {
        moveTo(Status.RELEASED);
    }

    private void moveTo(Status target) {
        boolean legal = switch (status) {
            case RESERVED -> target == Status.CONFIRMED || target == Status.RELEASED;
            case CONFIRMED -> target == Status.RELEASED;
            case RELEASED -> false;
        };
        if (!legal) {
            throw new SeatingException(Reason.ILLEGAL_TRANSITION,
                    "Seat " + id + ": illegal transition " + status + " -> " + target);
        }
        status = target;
    }
}
