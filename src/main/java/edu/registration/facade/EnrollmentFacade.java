package edu.registration.facade;

import edu.registration.eligibility.EligibilityException;
import edu.registration.eligibility.EligibilityService;
import edu.registration.eligibility.EnrollmentHold;
import edu.registration.finalization.FinalizationException;
import edu.registration.finalization.FinalizationService;
import edu.registration.finalization.Invoice;
import edu.registration.seating.SeatAllocator;
import edu.registration.seating.SeatAssignment;
import edu.registration.seating.SeatingException;

import java.util.ArrayList;
import java.util.List;

/**
 * The client-facing facade: one call, "enroll this student in this course", drives
 * A (eligibility hold) -> B (seat) -> C (invoice + transcript) and keeps the chain's progress
 * on disk after every step.
 *
 * If B fails, only A is undone. If C fails, B and then A are undone.
 * After a restart {@link #resumeInterrupted()} reads the persisted chain rows and either carries on
 * or finishes the undo, depending on what is actually on disk.
 */
public final class EnrollmentFacade {
    private final EligibilityService eligibility;
    private final SeatAllocator seating;
    private final FinalizationService finalization;
    private final ChainRepository chains;
    private final ChainCompensator compensator;

    public EnrollmentFacade(EligibilityService eligibility, SeatAllocator seating,
                            FinalizationService finalization, ChainRepository chains) {
        this.eligibility = eligibility;
        this.seating = seating;
        this.finalization = finalization;
        this.chains = chains;
        this.compensator = new ChainCompensator(eligibility, seating, finalization, chains);
    }

    /**
     * Enrolls the student. Throws {@link InvalidRequestException} for bad input (nothing is written) or
     * {@link EnrollmentFailedException} if a step failed (everything already done has been undone).
     */
    public EnrollmentReceipt enroll(String studentId, String courseId) {
        ChainRecord chain = startChain(studentId, courseId);
        return drive(chain.id(), null);
    }

    /** Withdraws a completed enrollment: invoice voided, transcript entry dropped, seat freed, hold revoked. */
    public List<String> drop(String studentId, String courseId) {
        requireText(studentId, "studentId");
        requireText(courseId, "courseId");
        ChainRecord chain = chains.findCompleted(studentId, courseId).orElseThrow(() ->
                new InvalidRequestException(studentId + " has no completed enrollment in " + courseId));
        return compensator.compensate(chain.id(), ChainStatus.DROPPED);
    }

    /**
     * Picks up every chain that was cut short (by a crash, say) and finishes it:
     * STARTED / PENDING_B / PENDING_C chains are driven forward, COMPENSATING chains finish their undo.
     * Returns one human-readable line per chain.
     */
    public List<String> resumeInterrupted() {
        List<String> report = new ArrayList<>();
        for (ChainRecord chain : chains.findInterrupted()) {
            try {
                if (chain.status() == ChainStatus.COMPENSATING) {
                    List<String> undone = compensator.compensate(chain.id(), ChainStatus.COMPENSATED);
                    report.add(chain.id() + ": finished the interrupted undo " + undone + " -> COMPENSATED");
                } else {
                    ChainStatus before = chain.status();
                    drive(chain.id(), null);
                    report.add(chain.id() + ": resumed from " + before + " -> COMPLETED");
                }
            } catch (EnrollmentFailedException e) {
                report.add(chain.id() + ": resumed but " + e.getMessage());
            }
        }
        return report;
    }

    /**
     * Demo/test hook that simulates a crash: runs the chain only as far as {@code crashAt}
     * (PENDING_B = after A, PENDING_C = after A and B) and stops, leaving the chain row on disk.
     * Returns the chain id.
     */
    public String enrollAndCrashAt(String studentId, String courseId, ChainStatus crashAt) {
        if (crashAt != ChainStatus.PENDING_B && crashAt != ChainStatus.PENDING_C) {
            throw new InvalidRequestException("The crash point must be PENDING_B or PENDING_C");
        }
        ChainRecord chain = startChain(studentId, courseId);
        drive(chain.id(), crashAt);
        return chain.id();
    }

    // ---------------------------------------------------------------- internals

    private ChainRecord startChain(String studentId, String courseId) {
        requireText(studentId, "studentId");
        requireText(courseId, "courseId");
        if (chains.findCompleted(studentId, courseId).isPresent()) {
            throw new InvalidRequestException(studentId + " is already enrolled in " + courseId);
        }
        ChainRecord chain = ChainRecord.started(chains.nextId(), studentId, courseId);
        chains.save(chain);
        return chain;
    }

    /** Runs whatever steps the persisted status says are still to do. Returns null only for a simulated crash. */
    private EnrollmentReceipt drive(String chainId, ChainStatus crashAt) {
        ChainRecord chain = chains.findById(chainId)
                .orElseThrow(() -> new InvalidRequestException("Unknown chain " + chainId));

        if (chain.status() == ChainStatus.STARTED) {                       // ---- step A
            try {
                EnrollmentHold hold = eligibility.requestAndApprove(chain.studentId(), chain.courseId());
                chains.save(chain.holdId(hold.id()).status(ChainStatus.PENDING_B));
            } catch (EligibilityException e) {
                chains.save(chain.status(ChainStatus.REJECTED).note("A: " + e.getMessage()));
                throw new EnrollmentFailedException(chainId, "A", ChainStatus.REJECTED, List.of(), e);
            }
        }
        if (crashAt == ChainStatus.PENDING_B) {
            return null;
        }

        if (chain.status() == ChainStatus.PENDING_B) {                     // ---- step B
            try {
                SeatAssignment seat = seating.reserve(chain.studentId(), chain.courseId());
                chains.save(chain.seatId(seat.id()).status(ChainStatus.PENDING_C));
            } catch (SeatingException e) {
                throw failAndCompensate(chain, "B", e);                    // undo A alone
            }
        }
        if (crashAt == ChainStatus.PENDING_C) {
            return null;
        }

        if (chain.status() == ChainStatus.PENDING_C) {                     // ---- step C
            try {
                FinalizationService.Receipt receipt =
                        finalization.finalizeEnrollment(chain.studentId(), chain.courseId());
                chains.save(chain.invoiceId(receipt.invoice().id()).entryId(receipt.entry().id()));
            } catch (FinalizationException e) {
                throw failAndCompensate(chain, "C", e);                    // undo B, then A
            }
            seating.confirm(chain.seatId());
            chains.save(chain.status(ChainStatus.COMPLETED));
        }

        if (chain.status() != ChainStatus.COMPLETED) {
            throw new InvalidRequestException("Chain " + chainId + " is " + chain.status()
                    + " and cannot be driven forward");
        }
        return receiptOf(chain);
    }

    private EnrollmentFailedException failAndCompensate(ChainRecord chain, String step, RuntimeException cause) {
        chains.save(chain.note(step + ": " + cause.getMessage()));
        List<String> undone = compensator.compensate(chain.id(), ChainStatus.COMPENSATED);
        return new EnrollmentFailedException(chain.id(), step, ChainStatus.COMPENSATED, undone, cause);
    }

    private EnrollmentReceipt receiptOf(ChainRecord chain) {
        Invoice invoice = finalization.findInvoice(chain.invoiceId()).orElseThrow(() ->
                new IllegalStateException("Chain " + chain.id() + " points at missing invoice " + chain.invoiceId()));
        return new EnrollmentReceipt(chain.id(), chain.studentId(), chain.courseId(),
                chain.holdId(), chain.seatId(), chain.invoiceId(), invoice.amount());
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(name + " must not be blank");
        }
    }
}
