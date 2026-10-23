package edu.registration.eligibility;

import edu.registration.DemoCatalog;
import edu.registration.eligibility.EligibilityException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class EligibilityTest {
    @TempDir
    Path dir;

    private DemoCatalog catalog;
    private EligibilityService service;

    @BeforeEach
    void setUp() {
        catalog = DemoCatalog.standard();
        service = newService();
    }

    private EligibilityService newService() {
        return new EligibilityService(
                new PrerequisiteChecker(catalog.prerequisites()),
                new FinancialHoldChecker(catalog.financialHoldThreshold()),
                new HoldRepository(dir), catalog.students());
    }

    private Reason reasonOf(Runnable action) {
        return assertThrows(EligibilityException.class, action::run).reason();
    }

    

    @Test
    void anEligibleStudentGetsAnApprovedHoldThatSurvivesARestart() {
        EnrollmentHold hold = service.requestAndApprove("S001", "CS201");

        assertEquals(EnrollmentHold.Status.APPROVED, hold.status());
        EnrollmentHold reloaded = newService().find(hold.id()).orElseThrow();
        assertEquals(EnrollmentHold.Status.APPROVED, reloaded.status());
        assertEquals("S001", reloaded.studentId());
        assertEquals("CS201", reloaded.courseId());
    }

    @Test
    void aMissingPrerequisiteIsRefusedAndNothingIsWritten() {
        assertEquals(Reason.PREREQUISITE_NOT_MET, reasonOf(() -> service.requestAndApprove("S002", "CS201")));
        assertTrue(service.all().isEmpty());
    }

    @Test
    void anUnpaidBalanceAboveTheThresholdBlocksRegistration() {
        assertEquals(Reason.FINANCIAL_HOLD, reasonOf(() -> service.requestAndApprove("S003", "CS201")));
        assertTrue(service.all().isEmpty());
    }

    @Test
    void unknownStudentsAndUnknownCoursesAreRefused() {
        assertEquals(Reason.UNKNOWN_STUDENT, reasonOf(() -> service.requestAndApprove("S999", "CS101")));
        assertEquals(Reason.UNKNOWN_COURSE, reasonOf(() -> service.requestAndApprove("S001", "ZZZ")));
    }

    @Test
    void askingTwiceReturnsTheSameActiveHold() {
        EnrollmentHold first = service.requestAndApprove("S001", "CS201");
        EnrollmentHold second = service.requestAndApprove("S001", "CS201");

        assertEquals(first.id(), second.id());
        assertEquals(1, service.all().size());
    }

    @Test
    void aHoldLeftPendingByACrashIsApprovedOnTheNextRequest() {
        new HoldRepository(dir).save(EnrollmentHold.pending("H-1", "S001", "CS201"));

        EnrollmentHold hold = service.requestAndApprove("S001", "CS201");

        assertEquals("H-1", hold.id());
        assertEquals(EnrollmentHold.Status.APPROVED, hold.status());
    }

    @Test
    void revokingIsIdempotentAndAFreshRequestAfterwardsCreatesANewHold() {
        EnrollmentHold hold = service.requestAndApprove("S001", "CS201");

        assertEquals(EnrollmentHold.Status.REVOKED, service.revoke(hold.id()).status());
        assertEquals(EnrollmentHold.Status.REVOKED, service.revoke(hold.id()).status());

        EnrollmentHold again = service.requestAndApprove("S001", "CS201");
        assertFalse(again.id().equals(hold.id()));
        assertEquals(EnrollmentHold.Status.APPROVED, again.status());
        assertEquals(again.id(), service.latestFor("S001", "CS201").orElseThrow().id());
    }

    @Test
    void revokingAHoldThatDoesNotExistIsAnError() {
        assertEquals(Reason.HOLD_NOT_FOUND, reasonOf(() -> service.revoke("H-404")));
    }

    @Test
    void latestForIsEmptyWhenThereIsNoHold() {
        assertTrue(service.latestFor("S001", "CS201").isEmpty());
    }

    

    @Test
    void prerequisiteCheckerListsExactlyWhatIsMissing() {
        PrerequisiteChecker checker = new PrerequisiteChecker(catalog.prerequisites());

        assertEquals(Set.of("CS101"), checker.missing("CS201", Set.of()));
        assertTrue(checker.missing("CS201", Set.of("CS101")).isEmpty());
        assertTrue(checker.knowsCourse("CS201"));
        assertFalse(checker.knowsCourse("NOPE"));
    }

    @Test
    void aBalanceEqualToTheThresholdIsStillFine() {
        FinancialHoldChecker checker = new FinancialHoldChecker(new BigDecimal("500"));

        assertFalse(checker.hasHold(new BigDecimal("500")));
        assertTrue(checker.hasHold(new BigDecimal("500.01")));
    }

    

    @Test
    void aHoldMayBeApprovedThenRevoked() {
        EnrollmentHold hold = EnrollmentHold.pending("H-1", "S001", "CS101");
        hold.approve();
        hold.revoke();

        assertEquals(EnrollmentHold.Status.REVOKED, hold.status());
        assertFalse(hold.isActive());
    }

    @Test
    void aPendingHoldMayBeRevokedDirectly() {
        EnrollmentHold hold = EnrollmentHold.pending("H-1", "S001", "CS101");
        hold.revoke();

        assertEquals(EnrollmentHold.Status.REVOKED, hold.status());
    }

    @Test
    void illegalTransitionsAreRefusedByTheHoldItself() {
        EnrollmentHold approved = EnrollmentHold.pending("H-1", "S001", "CS101");
        approved.approve();
        assertEquals(Reason.ILLEGAL_TRANSITION, reasonOf(approved::approve));

        approved.revoke();
        assertEquals(Reason.ILLEGAL_TRANSITION, reasonOf(approved::approve));
        assertEquals(Reason.ILLEGAL_TRANSITION, reasonOf(approved::revoke));
    }
}
