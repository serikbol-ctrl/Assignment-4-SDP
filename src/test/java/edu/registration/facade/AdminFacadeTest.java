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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The narrow administrative facade: inspection, manual compensation, fault injection. */
class AdminFacadeTest {
    @TempDir
    Path dir;

    private UniversityDsl uni;
    private RegistrarAdminFacade admin;

    @BeforeEach
    void aFreshUniversity() {
        uni = UniversityDsl.aFreshUniversityIn(dir);
        admin = uni.system().admin();
    }

    @Test
    void inspectingShowsTheLiveStateEachSubsystemHoldsForTheChain() {
        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS201");

        ChainReport report = admin.inspect(receipt.chainId());

        assertEquals(ChainStatus.COMPLETED, report.chainStatus());
        assertTrue(report.hold().contains("APPROVED"));
        assertTrue(report.seat().contains("CONFIRMED"));
        assertTrue(report.invoice().contains("ISSUED"));
        assertTrue(report.transcript().contains("ENROLLED"));
        assertTrue(report.describe().contains("student=S001"));
    }

    @Test
    void inspectingAnUnfinishedChainShowsDashesForStepsNotReached() {
        String chainId = uni.student("S001").isInterruptedWhileEnrollingIn("CS201", ChainStatus.PENDING_B);

        ChainReport report = admin.inspect(chainId);

        assertTrue(report.hold().contains("APPROVED"));
        assertEquals("-", report.seat());
        assertEquals("-", report.invoice());
        assertEquals("-", report.transcript());
    }

    @Test
    void inspectingShowsAFailureNoteAndFlagsIdsThatPointNowhere() {
        uni.system().chains().save(new ChainRecord("C-1", "S001", "CS101", ChainStatus.PENDING_C,
                "H-99", "S-99", "INV-99", "T-99", "manually damaged row"));

        String text = admin.inspect("C-1").describe();

        assertTrue(text.contains("H-99 MISSING"));
        assertTrue(text.contains("S-99 MISSING"));
        assertTrue(text.contains("INV-99 MISSING"));
        assertTrue(text.contains("T-99 MISSING"));
        assertTrue(text.contains("manually damaged row"));
    }

    @Test
    void inspectingAnUnknownChainIsAnInvalidRequest() {
        assertThrows(InvalidRequestException.class, () -> admin.inspect("C-404"));
    }

    @Test
    void manuallyCompensatingACompletedChainUndoesCThenBThenA() {
        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS201");

        List<String> undone = admin.compensate(receipt.chainId());

        assertEquals(List.of("C", "B", "A"), undone);
        uni.expectInvoice("S001", "CS201", Invoice.Status.VOIDED);
        uni.expectTranscript("S001", "CS201", TranscriptEntry.Status.DROPPED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.RELEASED);
        uni.expectHold("S001", "CS201", EnrollmentHold.Status.REVOKED);
        uni.expectChain(receipt.chainId(), ChainStatus.COMPENSATED);
    }

    @Test
    void manuallyCompensatingAnInterruptedChainUndoesOnlyWhatWasDone() {
        String afterA = uni.student("S001").isInterruptedWhileEnrollingIn("CS101", ChainStatus.PENDING_B);
        String afterB = uni.student("S004").isInterruptedWhileEnrollingIn("CS101", ChainStatus.PENDING_C);

        assertEquals(List.of("A"), admin.compensate(afterA));
        assertEquals(List.of("B", "A"), admin.compensate(afterB));
    }

    @Test
    void compensatingAChainThatIsAlreadyClosedIsRefused() {
        EnrollmentFailedException failure = uni.student("S002").failsToEnrollIn("CS201");

        assertThrows(InvalidRequestException.class, () -> admin.compensate(failure.chainId()));
        assertThrows(InvalidRequestException.class, () -> admin.compensate("C-404"));
    }

    @Test
    void listingShowsEveryChainInTheOrderTheyWereStarted() {
        uni.student("S001").enrollsIn("CS101");
        uni.student("S002").failsToEnrollIn("CS201");

        List<ChainRecord> chains = admin.listChains();

        assertEquals(2, chains.size());
        assertEquals(ChainStatus.COMPLETED, chains.get(0).status());
        assertEquals(ChainStatus.REJECTED, chains.get(1).status());
        assertTrue(admin.findInterrupted().isEmpty());
    }

    @Test
    void theGatewaySwitchIsAnAdminOperation() {
        assertTrue(admin.isBillingGatewayAvailable());
        admin.setBillingGatewayAvailable(false);
        assertFalse(admin.isBillingGatewayAvailable());
    }

    @Test
    void seatsAndWaitlistsCanBeInspected() {
        uni.student("S001").enrollsIn("CS201");
        uni.student("S004").failsToEnrollIn("CS201");

        assertEquals(0, admin.remainingSeats("CS201"));
        assertEquals(List.of("S004"), admin.waitlistOf("CS201"));
    }
}
