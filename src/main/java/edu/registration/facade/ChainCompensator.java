package edu.registration.facade;

import edu.registration.eligibility.EligibilityService;
import edu.registration.finalization.FinalizationService;
import edu.registration.seating.SeatAllocator;

import java.util.ArrayList;
import java.util.List;






final class ChainCompensator {
    private final EligibilityService eligibility;
    private final SeatAllocator seating;
    private final FinalizationService finalization;
    private final ChainRepository chains;

    ChainCompensator(EligibilityService eligibility, SeatAllocator seating,
                     FinalizationService finalization, ChainRepository chains) {
        this.eligibility = eligibility;
        this.seating = seating;
        this.finalization = finalization;
        this.chains = chains;
    }

     
    List<String> compensate(String chainId, ChainStatus finalStatus) {
        ChainRecord chain = chains.findById(chainId)
                .orElseThrow(() -> new InvalidRequestException("Unknown chain " + chainId));
        chains.save(chain.status(ChainStatus.COMPENSATING));      

        List<String> undone = new ArrayList<>();
        if (chain.invoiceId() != null || chain.entryId() != null) {
            finalization.cancel(chain.invoiceId(), chain.entryId());
            undone.add("C");
        }
        if (chain.seatId() != null) {
            seating.release(chain.seatId());
            undone.add("B");
        }
        if (chain.holdId() != null) {
            eligibility.revoke(chain.holdId());
            undone.add("A");
        }
        chains.save(chain.status(finalStatus));
        return undone;
    }
}
