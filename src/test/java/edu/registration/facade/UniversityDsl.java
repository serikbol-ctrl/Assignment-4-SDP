package edu.registration.facade;

import edu.registration.RegistrarSystem;
import edu.registration.eligibility.EnrollmentHold;
import edu.registration.finalization.Invoice;
import edu.registration.finalization.TranscriptEntry;
import edu.registration.seating.SeatAssignment;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tiny domain-specific testing language, so a test reads like a sentence about a university:
 *
 *   uni.student("S001").enrollsIn("CS201");
 *   uni.billingGatewayGoesDown();
 *   EnrollmentFailedException failure = uni.student("S005").failsToEnrollIn("MATH101");
 *   uni.expectSeat("S005", "MATH101", SeatAssignment.Status.RELEASED);
 *   uni.restarts();
 *
 * Every test builds its own university in its own temporary directory, so tests never share state.
 */
final class UniversityDsl {
    private final Path dataDir;
    private RegistrarSystem system;

    private UniversityDsl(Path dataDir) {
        this.dataDir = dataDir;
        this.system = RegistrarSystem.open(dataDir);
    }

    static UniversityDsl aFreshUniversityIn(Path dataDir) {
        return new UniversityDsl(dataDir);
    }

    /** Simulates stopping and starting the application: new objects, same files on disk. */
    UniversityDsl restarts() {
        system = RegistrarSystem.open(dataDir);
        return this;
    }

    UniversityDsl billingGatewayGoesDown() {
        system.admin().setBillingGatewayAvailable(false);
        return this;
    }

    UniversityDsl billingGatewayComesBack() {
        system.admin().setBillingGatewayAvailable(true);
        return this;
    }

    RegistrarSystem system() {
        return system;
    }

    Student student(String studentId) {
        return new Student(studentId);
    }

    final class Student {
        private final String id;

        private Student(String id) {
            this.id = id;
        }

        EnrollmentReceipt enrollsIn(String course) {
            return system.enrollment().enroll(id, course);
        }

        /** Enrolls and expects the chain to break; returns the failure for further assertions. */
        EnrollmentFailedException failsToEnrollIn(String course) {
            try {
                system.enrollment().enroll(id, course);
            } catch (EnrollmentFailedException e) {
                return e;
            }
            throw new AssertionError("Expected enrolling " + id + " in " + course + " to fail, but it succeeded");
        }

        List<String> dropsOut(String course) {
            return system.enrollment().drop(id, course);
        }

        /** Starts enrolling but "crashes" after the given step. Returns the chain id left on disk. */
        String isInterruptedWhileEnrollingIn(String course, ChainStatus crashAt) {
            return system.enrollment().enrollAndCrashAt(id, course, crashAt);
        }
    }

    // ---------------------------------------------------------------- expectations (read from disk)

    void expectHold(String student, String course, EnrollmentHold.Status expected) {
        assertEquals(Optional.of(expected),
                system.eligibility().latestFor(student, course).map(EnrollmentHold::status),
                "A: hold of " + student + " in " + course);
    }

    void expectNoHold(String student, String course) {
        assertTrue(system.eligibility().latestFor(student, course).isEmpty(), "A: no hold expected");
    }

    void expectSeat(String student, String course, SeatAssignment.Status expected) {
        assertEquals(Optional.of(expected),
                system.seating().latestFor(student, course).map(SeatAssignment::status),
                "B: seat of " + student + " in " + course);
    }

    void expectNoSeat(String student, String course) {
        assertTrue(system.seating().latestFor(student, course).isEmpty(), "B: no seat expected");
    }

    void expectInvoice(String student, String course, Invoice.Status expected) {
        assertEquals(Optional.of(expected),
                system.finalization().latestInvoiceFor(student, course).map(Invoice::status),
                "C: invoice of " + student + " for " + course);
    }

    void expectNoInvoice(String student, String course) {
        assertTrue(system.finalization().latestInvoiceFor(student, course).isEmpty(), "C: no invoice expected");
    }

    void expectTranscript(String student, String course, TranscriptEntry.Status expected) {
        assertEquals(Optional.of(expected),
                system.finalization().latestEntryFor(student, course).map(TranscriptEntry::status),
                "C: transcript entry of " + student + " for " + course);
    }

    void expectNoTranscriptEntry(String student, String course) {
        assertTrue(system.finalization().latestEntryFor(student, course).isEmpty(), "C: no transcript entry expected");
    }

    void expectChain(String chainId, ChainStatus expected) {
        assertEquals(Optional.of(expected),
                system.chains().findById(chainId).map(ChainRecord::status),
                "chain " + chainId);
    }
}
