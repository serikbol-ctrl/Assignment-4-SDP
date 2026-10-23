package edu.registration.facade;

import edu.registration.eligibility.EligibilityService;
import edu.registration.finalization.FinalizationService;
import edu.registration.seating.SeatAllocator;

import java.util.List;

/**
 * The narrow administrative facade: inspect what is on disk, compensate a chain by hand,
 * and switch the billing gateway off or on to provoke a failure at step C.
 * It shares the persisted chain table with {@link EnrollmentFacade} but never runs the chain itself.
 */
public final class RegistrarAdminFacade {
    private final EligibilityService eligibility;
    private final SeatAllocator seating;
    private final FinalizationService finalization;
    private final ChainRepository chains;
    private final ChainCompensator compensator;

    public RegistrarAdminFacade(EligibilityService eligibility, SeatAllocator seating,
                                FinalizationService finalization, ChainRepository chains) {
        this.eligibility = eligibility;
        this.seating = seating;
        this.finalization = finalization;
        this.chains = chains;
        this.compensator = new ChainCompensator(eligibility, seating, finalization, chains);
    }

    public List<ChainRecord> listChains() {
        return chains.findAll();
    }

    /** Chains that were cut short and wait for {@code EnrollmentFacade.resumeInterrupted()}. */
    public List<ChainRecord> findInterrupted() {
        return chains.findInterrupted();
    }

    /** Joins the chain row with the live state of the hold, seat, invoice and transcript entry it points at. */
    public ChainReport inspect(String chainId) {
        ChainRecord c = chains.findById(chainId)
                .orElseThrow(() -> new InvalidRequestException("Unknown chain " + chainId));
        String hold = c.holdId() == null ? "-"
                : eligibility.find(c.holdId()).map(h -> h.id() + " " + h.status()).orElse(c.holdId() + " MISSING");
        String seat = c.seatId() == null ? "-"
                : seating.find(c.seatId()).map(s -> s.id() + " " + s.status()).orElse(c.seatId() + " MISSING");
        String invoice = c.invoiceId() == null ? "-"
                : finalization.findInvoice(c.invoiceId())
                        .map(i -> i.id() + " " + i.status() + " " + i.amount()).orElse(c.invoiceId() + " MISSING");
        String transcript = c.entryId() == null ? "-"
                : finalization.findEntry(c.entryId()).map(e -> e.id() + " " + e.status()).orElse(c.entryId() + " MISSING");
        return new ChainReport(c.id(), c.studentId(), c.courseId(), c.status(), c.note(),
                hold, seat, invoice, transcript);
    }

    /** Manual compensation of an unfinished or completed chain. Returns the undone steps, in order. */
    public List<String> compensate(String chainId) {
        ChainRecord c = chains.findById(chainId)
                .orElseThrow(() -> new InvalidRequestException("Unknown chain " + chainId));
        if (!c.status().canBeCompensated()) {
            throw new InvalidRequestException("Chain " + chainId + " is " + c.status() + "; nothing to compensate");
        }
        return compensator.compensate(chainId, ChainStatus.COMPENSATED);
    }

    public void setBillingGatewayAvailable(boolean available) {
        finalization.setGatewayAvailable(available);
    }

    public boolean isBillingGatewayAvailable() {
        return finalization.isGatewayAvailable();
    }

    public int remainingSeats(String courseId) {
        return seating.remainingSeats(courseId);
    }

    public List<String> waitlistOf(String courseId) {
        return seating.waitlistOf(courseId);
    }
}
