package edu.registration.facade;

 
public record ChainReport(String chainId, String studentId, String courseId, ChainStatus chainStatus,
                          String note, String hold, String seat, String invoice, String transcript) {

    public String describe() {
        return String.join("\n",
                "Chain " + chainId + "  student=" + studentId + "  course=" + courseId,
                "  chain status : " + chainStatus + (note == null ? "" : "   (" + note + ")"),
                "  A hold       : " + hold,
                "  B seat       : " + seat,
                "  C invoice    : " + invoice,
                "  C transcript : " + transcript);
    }
}
