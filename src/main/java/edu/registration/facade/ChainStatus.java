package edu.registration.facade;













public enum ChainStatus {
    STARTED, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED, REJECTED, DROPPED;

     
    public boolean isInterrupted() {
        return this == STARTED || this == PENDING_B || this == PENDING_C || this == COMPENSATING;
    }

    public boolean canBeCompensated() {
        return isInterrupted() || this == COMPLETED;
    }
}
