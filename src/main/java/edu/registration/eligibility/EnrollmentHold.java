package edu.registration.eligibility;

import edu.registration.eligibility.EligibilityException.Reason;

/**
 * Subsystem A's entity: a hold that says "this student is allowed to take this course".
 * It polices its own life cycle: PENDING -> APPROVED -> REVOKED (or PENDING -> REVOKED).
 * REVOKED is final.
 */
public final class EnrollmentHold {
    public enum Status { PENDING, APPROVED, REVOKED }

    private final String id;
    private final String studentId;
    private final String courseId;
    private Status status;

    public EnrollmentHold(String id, String studentId, String courseId, Status status) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = status;
    }

    public static EnrollmentHold pending(String id, String studentId, String courseId) {
        return new EnrollmentHold(id, studentId, courseId, Status.PENDING);
    }

    public String id() { return id; }
    public String studentId() { return studentId; }
    public String courseId() { return courseId; }
    public Status status() { return status; }

    public boolean isActive() {
        return status != Status.REVOKED;
    }

    public void approve() {
        moveTo(Status.APPROVED);
    }

    public void revoke() {
        moveTo(Status.REVOKED);
    }

    private void moveTo(Status target) {
        boolean legal = switch (status) {
            case PENDING -> target == Status.APPROVED || target == Status.REVOKED;
            case APPROVED -> target == Status.REVOKED;
            case REVOKED -> false;
        };
        if (!legal) {
            throw new EligibilityException(Reason.ILLEGAL_TRANSITION,
                    "Hold " + id + ": illegal transition " + status + " -> " + target);
        }
        status = target;
    }
}
