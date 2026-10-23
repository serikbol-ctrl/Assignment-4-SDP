package edu.registration.facade;

import java.util.List;





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

     
    public String failedStep() { return failedStep; }

    public ChainStatus finalStatus() { return finalStatus; }

     
    public List<String> compensatedSteps() { return compensatedSteps; }
}
