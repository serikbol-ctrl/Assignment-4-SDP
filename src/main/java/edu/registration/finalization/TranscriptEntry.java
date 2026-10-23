package edu.registration.finalization;

import edu.registration.finalization.FinalizationException.Reason;

 
public final class TranscriptEntry {
    public enum Status { ENROLLED, DROPPED, COMPLETED }

    private final String id;
    private final String studentId;
    private final String courseId;
    private Status status;

    public TranscriptEntry(String id, String studentId, String courseId, Status status) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = status;
    }

    public static TranscriptEntry enrolled(String id, String studentId, String courseId) {
        return new TranscriptEntry(id, studentId, courseId, Status.ENROLLED);
    }

    public String id() { return id; }
    public String studentId() { return studentId; }
    public String courseId() { return courseId; }
    public Status status() { return status; }

    public boolean isActive() {
        return status == Status.ENROLLED;
    }

    public void drop() {
        moveTo(Status.DROPPED);
    }

    public void complete() {
        moveTo(Status.COMPLETED);
    }

    private void moveTo(Status target) {
        boolean legal = status == Status.ENROLLED && target != Status.ENROLLED;
        if (!legal) {
            throw new FinalizationException(Reason.ILLEGAL_TRANSITION,
                    "Transcript entry " + id + ": illegal transition " + status + " -> " + target);
        }
        status = target;
    }
}
