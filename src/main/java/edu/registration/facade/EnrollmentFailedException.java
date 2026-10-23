package edu.registration.facade;

import java.util.List;

/**
 * The chain could not be completed. Carries everything a client needs to know: which step failed,
 * which steps were undone (in the order they were undone), and the final persisted chain status.
 */
public class EnrollmentFailedException extends RuntimeException {
    private final String chainId;
    private final String failedStep;
    private final ChainStatus finalStatus;
    private final List<String> compensatedSteps;

    public EnrollmentFailedException(String chainId, String failedStep, ChainStatus finalStatus,
                                     List<String> compensatedSteps, Throwable cause) {
        super("Enrollment " + chainId + " failed at step " + failedStep + ": " + cause.getMessage()
                + (compensatedSteps.isEmpty() ? "" : " (compensated " + compensatedSteps + ")")
                + " -> " + finalStatus, cause);
        this.chainId = chainId;
        this.failedStep = failedStep;
        this.finalStatus = finalStatus;
        this.compensatedSteps = List.copyOf(compensatedSteps);
    }

    public String chainId() { return chainId; }

    /** "A", "B" or "C". */
    public String failedStep() { return failedStep; }

    public ChainStatus finalStatus() { return finalStatus; }

    /** Steps that were undone, in order, e.g. [B, A]. */
    public List<String> compensatedSteps() { return compensatedSteps; }
}
