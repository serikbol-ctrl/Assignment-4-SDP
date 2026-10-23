package edu.registration.facade;

import edu.registration.eligibility.EnrollmentHold;
import edu.registration.finalization.Invoice;
import edu.registration.finalization.TranscriptEntry;
import edu.registration.seating.SeatAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Restart" = open a brand-new RegistrarSystem over the same directory. Nothing from the old objects
 * survives; the facade has to work out what to do from the rows on disk.
 */
class PersistenceReloadTest {
    @TempDir
    Path dir;

    private UniversityDsl uni;

    @BeforeEach
    void aFreshUniversity() {
        uni = UniversityDsl.aFreshUniversityIn(dir);
    }

    @Test
    void aCompletedEnrollmentIsStillThereAfterARestart() {
        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS201");

        uni.restarts();

        uni.expectHold("S001", "CS201", EnrollmentHold.Status.APPROVED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.CONFIRMED);
        uni.expectInvoice("S001", "CS201", Invoice.Status.ISSUED);
        uni.expectTranscript("S001", "CS201", TranscriptEntry.Status.ENROLLED);
        ChainRecord row = uni.system().chains().findById(receipt.chainId()).orElseThrow();
        assertEquals(ChainStatus.COMPLETED, row.status());
        assertEquals(receipt.holdId(), row.holdId());
        assertEquals(receipt.seatId(), row.seatId());
        assertEquals(receipt.invoiceId(), row.invoiceId());
    }

    @Test
    void aRestartedFacadeStillKnowsWhoIsAlreadyEnrolled() {
        uni.student("S001").enrollsIn("CS201");

        uni.restarts();

        assertThrows(InvalidRequestException.class, () -> uni.student("S001").enrollsIn("CS201"));
        assertEquals(0, uni.system().admin().remainingSeats("CS201"));
    }

    @Test
    void aChainInterruptedAfterAIsResumedFromPendingBAfterARestart() {
        String chainId = uni.student("S001").isInterruptedWhileEnrollingIn("CS201", ChainStatus.PENDING_B);
        uni.expectChain(chainId, ChainStatus.PENDING_B);

        uni.restarts();

        uni.expectHold("S001", "CS201", EnrollmentHold.Status.APPROVED);    // what A did is on disk
        uni.expectNoSeat("S001", "CS201");                                  // B had not started
        assertEquals(1, uni.system().admin().findInterrupted().size());

        List<String> report = uni.system().enrollment().resumeInterrupted();

        assertEquals(1, report.size());
        assertTrue(report.get(0).contains("resumed from PENDING_B"));
        uni.expectChain(chainId, ChainStatus.COMPLETED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.CONFIRMED);
        uni.expectInvoice("S001", "CS201", Invoice.Status.ISSUED);
        assertTrue(uni.system().admin().findInterrupted().isEmpty());
    }

    @Test
    void aChainInterruptedAfterBIsResumedFromPendingCAfterARestart() {
        String chainId = uni.student("S001").isInterruptedWhileEnrollingIn("CS201", ChainStatus.PENDING_C);

        uni.restarts();

        uni.expectSeat("S001", "CS201", SeatAssignment.Status.RESERVED);
        uni.expectNoInvoice("S001", "CS201");

        List<String> report = uni.system().enrollment().resumeInterrupted();

        assertTrue(report.get(0).contains("resumed from PENDING_C"));
        uni.expectChain(chainId, ChainStatus.COMPLETED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.CONFIRMED);
        uni.expectTranscript("S001", "CS201", TranscriptEntry.Status.ENROLLED);
    }

    @Test
    void ifCStillFailsAfterTheRestartBThenAAreUndoneFromWhatIsOnDisk() {
        String chainId = uni.student("S005").isInterruptedWhileEnrollingIn("MATH101", ChainStatus.PENDING_C);

        uni.restarts().billingGatewayGoesDown();
        List<String> report = uni.system().enrollment().resumeInterrupted();

        assertTrue(report.get(0).contains("[B, A]"));
        uni.expectChain(chainId, ChainStatus.COMPENSATED);
        uni.expectSeat("S005", "MATH101", SeatAssignment.Status.RELEASED);
        uni.expectHold("S005", "MATH101", EnrollmentHold.Status.REVOKED);
        uni.expectNoInvoice("S005", "MATH101");
    }

    @Test
    void aChainThatNeverGotPastStartedIsRunFromTheBeginning() {
        uni.system().chains().save(ChainRecord.started("C-1", "S001", "CS101"));

        uni.restarts();
        uni.system().enrollment().resumeInterrupted();

        uni.expectChain("C-1", ChainStatus.COMPLETED);
        uni.expectInvoice("S001", "CS101", Invoice.Status.ISSUED);
    }

    @Test
    void aResumedChainThatTurnsOutToBeRejectedIsReportedAndClosed() {
        uni.system().chains().save(ChainRecord.started("C-1", "S002", "CS201"));

        uni.restarts();
        List<String> report = uni.system().enrollment().resumeInterrupted();

        assertTrue(report.get(0).contains("failed at step A"));
        uni.expectChain("C-1", ChainStatus.REJECTED);
    }

    @Test
    void anInterruptedUndoIsFinishedAfterARestart() {
        EnrollmentHold hold = uni.system().eligibility().requestAndApprove("S001", "CS201");
        SeatAssignment seat = uni.system().seating().reserve("S001", "CS201");
        uni.system().chains().save(new ChainRecord("C-1", "S001", "CS201", ChainStatus.COMPENSATING,
                hold.id(), seat.id(), null, null, "crashed while undoing"));

        uni.restarts();
        List<String> report = uni.system().enrollment().resumeInterrupted();

        assertTrue(report.get(0).contains("[B, A]"));
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.RELEASED);
        uni.expectHold("S001", "CS201", EnrollmentHold.Status.REVOKED);
        uni.expectChain("C-1", ChainStatus.COMPENSATED);
    }

    @Test
    void anUndoThatWasHalfDoneIsCompletedWithoutRepeatingTheFinishedPart() {
        EnrollmentHold hold = uni.system().eligibility().requestAndApprove("S001", "CS201");
        SeatAssignment seat = uni.system().seating().reserve("S001", "CS201");
        uni.system().seating().release(seat.id());                          // B was already undone before the crash
        uni.system().chains().save(new ChainRecord("C-1", "S001", "CS201", ChainStatus.COMPENSATING,
                hold.id(), seat.id(), null, null, null));

        uni.restarts();
        uni.system().enrollment().resumeInterrupted();

        uni.expectSeat("S001", "CS201", SeatAssignment.Status.RELEASED);
        uni.expectHold("S001", "CS201", EnrollmentHold.Status.REVOKED);
        uni.expectChain("C-1", ChainStatus.COMPENSATED);
    }

    @Test
    void resumingWhenNothingIsInterruptedDoesNothing() {
        uni.student("S001").enrollsIn("CS101");
        uni.restarts();

        assertTrue(uni.system().enrollment().resumeInterrupted().isEmpty());
    }
}
