package edu.registration.finalization;

import edu.registration.finalization.FinalizationException.Reason;

import java.math.BigDecimal;

 
public final class Invoice {
    public enum Status { ISSUED, PAID, VOIDED }

    private final String id;
    private final String studentId;
    private final String courseId;
    private final BigDecimal amount;
    private Status status;

    public Invoice(String id, String studentId, String courseId, BigDecimal amount, Status status) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.amount = amount;
        this.status = status;
    }

    public static Invoice issue(String id, String studentId, String courseId, BigDecimal amount) {
        return new Invoice(id, studentId, courseId, amount, Status.ISSUED);
    }

    public String id() { return id; }
    public String studentId() { return studentId; }
    public String courseId() { return courseId; }
    public BigDecimal amount() { return amount; }
    public Status status() { return status; }

    public boolean isActive() {
        return status != Status.VOIDED;
    }

    public void markPaid() {
        moveTo(Status.PAID);
    }

    public void voidIt() {
        moveTo(Status.VOIDED);
    }

    private void moveTo(Status target) {
        boolean legal = switch (status) {
            case ISSUED -> target == Status.PAID || target == Status.VOIDED;
            case PAID -> target == Status.VOIDED;
            case VOIDED -> false;
        };
        if (!legal) {
            throw new FinalizationException(Reason.ILLEGAL_TRANSITION,
                    "Invoice " + id + ": illegal transition " + status + " -> " + target);
        }
        status = target;
    }
}
