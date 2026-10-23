package edu.registration.facade;

import edu.registration.eligibility.EligibilityException;
import edu.registration.eligibility.EnrollmentHold;
import edu.registration.finalization.FinalizationException;
import edu.registration.finalization.Invoice;
import edu.registration.finalization.TranscriptEntry;
import edu.registration.seating.SeatAssignment;
import edu.registration.seating.SeatingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class EnrollmentFacadeTest {
    @TempDir
    Path dir;

    private UniversityDsl uni;

    @BeforeEach
    void aFreshUniversity() {
        uni = UniversityDsl.aFreshUniversityIn(dir);
    }

    

    @Test
    void aSuccessfulEnrollmentRunsAllThreeStepsAndLeavesEverythingOnDisk() {
        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS201");

        assertEquals(0, receipt.amount().compareTo(new BigDecimal("620")));
        uni.expectHold("S001", "CS201", EnrollmentHold.Status.APPROVED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.CONFIRMED);
        uni.expectInvoice("S001", "CS201", Invoice.Status.ISSUED);
        uni.expectTranscript("S001", "CS201", TranscriptEntry.Status.ENROLLED);
        uni.expectChain(receipt.chainId(), ChainStatus.COMPLETED);
    }

    @Test
    void enrollingTwiceInTheSameCourseIsRefusedBeforeAnythingIsWritten() {
        uni.student("S001").enrollsIn("CS201");

        assertThrows(InvalidRequestException.class, () -> uni.student("S001").enrollsIn("CS201"));
        assertEquals(1, uni.system().chains().findAll().size());
    }

    

    @Test
    void aMissingPrerequisiteIsRejectedAtAAndNothingWasCommittedSoNothingIsUndone() {
        EnrollmentFailedException failure = uni.student("S002").failsToEnrollIn("CS201");

        assertEquals("A", failure.failedStep());
        assertTrue(failure.compensatedSteps().isEmpty());
        assertEquals(ChainStatus.REJECTED, failure.finalStatus());
        assertTrue(failure.getCause() instanceof EligibilityException);
        uni.expectNoHold("S002", "CS201");
        uni.expectNoSeat("S002", "CS201");
        uni.expectNoInvoice("S002", "CS201");
        uni.expectChain(failure.chainId(), ChainStatus.REJECTED);
    }

    @Test
    void aFinancialHoldIsRejectedAtA() {
        EnrollmentFailedException failure = uni.student("S003").failsToEnrollIn("CS201");

        assertEquals("A", failure.failedStep());
        assertEquals(ChainStatus.REJECTED, failure.finalStatus());
        uni.expectNoHold("S003", "CS201");
    }

    @Test
    void unknownStudentsAndCoursesAreRejectedAtA() {
        assertEquals("A", uni.student("S999").failsToEnrollIn("CS101").failedStep());
        assertEquals("A", uni.student("S001").failsToEnrollIn("ZZZ").failedStep());
    }

    @Test
    void blankInputIsAnInvalidRequestAndCreatesNoChainAtAll() {
        assertThrows(InvalidRequestException.class, () -> uni.system().enrollment().enroll(" ", "CS101"));
        assertThrows(InvalidRequestException.class, () -> uni.system().enrollment().enroll("S001", null));
        assertThrows(InvalidRequestException.class, () -> uni.system().enrollment().enroll("S001", ""));

        assertTrue(uni.system().chains().findAll().isEmpty());
    }

    

    @Test
    void whenBFailsAfterAHasCommittedOnlyAIsCompensated() {
        uni.student("S001").enrollsIn("CS201");                             

        EnrollmentFailedException failure = uni.student("S004").failsToEnrollIn("CS201");

        assertEquals("B", failure.failedStep());
        assertEquals(List.of("A"), failure.compensatedSteps());
        assertEquals(ChainStatus.COMPENSATED, failure.finalStatus());
        assertTrue(failure.getCause() instanceof SeatingException);

        uni.expectHold("S004", "CS201", EnrollmentHold.Status.REVOKED);     
        uni.expectNoSeat("S004", "CS201");                                  
        uni.expectNoInvoice("S004", "CS201");                               
        uni.expectNoTranscriptEntry("S004", "CS201");
        uni.expectChain(failure.chainId(), ChainStatus.COMPENSATED);
        assertEquals(List.of("S004"), uni.system().admin().waitlistOf("CS201"));
    }

    @Test
    void theFirstStudentKeepsHisSeatWhenSomeoneElseFailsAtB() {
        uni.student("S001").enrollsIn("CS201");
        uni.student("S004").failsToEnrollIn("CS201");

        uni.expectHold("S001", "CS201", EnrollmentHold.Status.APPROVED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.CONFIRMED);
        uni.expectInvoice("S001", "CS201", Invoice.Status.ISSUED);
    }

    

    @Test
    void whenCFailsAfterAAndBHaveCommittedBIsUndoneThenA() {
        uni.billingGatewayGoesDown();

        EnrollmentFailedException failure = uni.student("S005").failsToEnrollIn("MATH101");

        assertEquals("C", failure.failedStep());
        assertEquals(List.of("B", "A"), failure.compensatedSteps());        
        assertEquals(ChainStatus.COMPENSATED, failure.finalStatus());
        assertTrue(failure.getCause() instanceof FinalizationException);

        uni.expectHold("S005", "MATH101", EnrollmentHold.Status.REVOKED);
        uni.expectSeat("S005", "MATH101", SeatAssignment.Status.RELEASED);
        uni.expectNoInvoice("S005", "MATH101");                             
        uni.expectNoTranscriptEntry("S005", "MATH101");
        uni.expectChain(failure.chainId(), ChainStatus.COMPENSATED);
        assertEquals(30, uni.system().admin().remainingSeats("MATH101"));
    }

    @Test
    void aSeatReleasedByCompensationCanBeTakenByAnotherStudentOnceBillingIsBack() {
        uni.billingGatewayGoesDown();
        uni.student("S001").failsToEnrollIn("CS201");                       
        uni.billingGatewayComesBack();

        EnrollmentReceipt receipt = uni.student("S004").enrollsIn("CS201");

        uni.expectChain(receipt.chainId(), ChainStatus.COMPLETED);
        uni.expectSeat("S004", "CS201", SeatAssignment.Status.CONFIRMED);
    }

    @Test
    void aStudentWhoFailedOnceCanSimplyTryAgain() {
        uni.billingGatewayGoesDown();
        uni.student("S001").failsToEnrollIn("CS101");
        uni.billingGatewayComesBack();

        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS101");

        uni.expectChain(receipt.chainId(), ChainStatus.COMPLETED);
        uni.expectHold("S001", "CS101", EnrollmentHold.Status.APPROVED);
        assertEquals(2, uni.system().chains().findAll().size());
    }

    

    @Test
    void droppingUndoesAllThreeStepsInReverseOrder() {
        EnrollmentReceipt receipt = uni.student("S001").enrollsIn("CS201");

        List<String> undone = uni.student("S001").dropsOut("CS201");

        assertEquals(List.of("C", "B", "A"), undone);
        uni.expectInvoice("S001", "CS201", Invoice.Status.VOIDED);
        uni.expectTranscript("S001", "CS201", TranscriptEntry.Status.DROPPED);
        uni.expectSeat("S001", "CS201", SeatAssignment.Status.RELEASED);
        uni.expectHold("S001", "CS201", EnrollmentHold.Status.REVOKED);
        uni.expectChain(receipt.chainId(), ChainStatus.DROPPED);
        assertEquals(1, uni.system().admin().remainingSeats("CS201"));
    }

    @Test
    void droppingACourseTheStudentIsNotEnrolledInIsAnInvalidRequest() {
        assertThrows(InvalidRequestException.class, () -> uni.student("S001").dropsOut("CS201"));
        assertThrows(InvalidRequestException.class, () -> uni.system().enrollment().drop("", "CS201"));
    }

    @Test
    void aStudentCanReEnrollAfterDropping() {
        uni.student("S001").enrollsIn("CS201");
        uni.student("S001").dropsOut("CS201");

        EnrollmentReceipt again = uni.student("S001").enrollsIn("CS201");

        uni.expectChain(again.chainId(), ChainStatus.COMPLETED);
        assertNotNull(again.holdId());
        assertFalse(again.holdId().equals("H-1"));
    }

    

    @Test
    void theCrashHookOnlyAcceptsPendingBOrPendingC() {
        assertThrows(InvalidRequestException.class,
                () -> uni.system().enrollment().enrollAndCrashAt("S001", "CS101", ChainStatus.COMPLETED));
        assertTrue(uni.system().chains().findAll().isEmpty());
    }
}
