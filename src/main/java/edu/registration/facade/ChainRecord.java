package edu.registration.facade;






public final class ChainRecord {
    private final String id;
    private final String studentId;
    private final String courseId;
    private ChainStatus status;
    private String holdId;
    private String seatId;
    private String invoiceId;
    private String entryId;
    private String note;

    public ChainRecord(String id, String studentId, String courseId, ChainStatus status,
                       String holdId, String seatId, String invoiceId, String entryId, String note) {
        this.id = id;
        this.studentId = studentId;
        this.courseId = courseId;
        this.status = status;
        this.holdId = holdId;
        this.seatId = seatId;
        this.invoiceId = invoiceId;
        this.entryId = entryId;
        this.note = note;
    }

    public static ChainRecord started(String id, String studentId, String courseId) {
        return new ChainRecord(id, studentId, courseId, ChainStatus.STARTED, null, null, null, null, null);
    }

    public String id() { return id; }
    public String studentId() { return studentId; }
    public String courseId() { return courseId; }
    public ChainStatus status() { return status; }
    public String holdId() { return holdId; }
    public String seatId() { return seatId; }
    public String invoiceId() { return invoiceId; }
    public String entryId() { return entryId; }
    public String note() { return note; }

    public ChainRecord status(ChainStatus status) { this.status = status; return this; }
    public ChainRecord holdId(String holdId) { this.holdId = holdId; return this; }
    public ChainRecord seatId(String seatId) { this.seatId = seatId; return this; }
    public ChainRecord invoiceId(String invoiceId) { this.invoiceId = invoiceId; return this; }
    public ChainRecord entryId(String entryId) { this.entryId = entryId; return this; }
    public ChainRecord note(String note) { this.note = note; return this; }
}
