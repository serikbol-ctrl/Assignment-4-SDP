package edu.registration.facade;

/**
 * Persisted progress of one enrollment chain. This is what the facade reads back after a restart.
 *
 *   STARTED       chain created, nothing confirmed yet
 *   PENDING_B     A is done (hold approved), B still to do
 *   PENDING_C     A and B are done (seat reserved), C still to do
 *   COMPLETED     A, B and C are done
 *   COMPENSATING  an undo is in progress (it may have been interrupted)
 *   COMPENSATED   everything that had been done was undone
 *   REJECTED      refused at step A, so there was nothing to undo
 *   DROPPED       a completed enrollment was withdrawn (all three steps undone)
 */
public enum ChainStatus {
    STARTED, PENDING_B, PENDING_C, COMPLETED, COMPENSATING, COMPENSATED, REJECTED, DROPPED;

    /** The chain was cut short and still needs the facade's attention. */
    public boolean isInterrupted() {
        return this == STARTED || this == PENDING_B || this == PENDING_C || this == COMPENSATING;
    }

    public boolean canBeCompensated() {
        return isInterrupted() || this == COMPLETED;
    }
}
