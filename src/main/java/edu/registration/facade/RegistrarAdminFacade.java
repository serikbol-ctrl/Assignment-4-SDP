package edu.registration.facade;

import edu.registration.eligibility.EligibilityService;
import edu.registration.finalization.FinalizationService;
import edu.registration.seating.SeatAllocator;

import java.util.List;






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

     
    public List<ChainRecord> findInterrupted() {
        return chains.findInterrupted();
    }

     
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
