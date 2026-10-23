package edu.registration.eligibility;

import edu.registration.eligibility.EligibilityException.Reason;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Subsystem A (intake): decides whether a student may take a course and records that decision
 * as a durable {@link EnrollmentHold}. Usable on its own, it knows nothing about subsystems B and C.
 * Both operations are idempotent, so a restarted chain can safely call them again.
 */
public final class EligibilityService {
    /** Reference data about a student, looked up by id. */
    public record StudentProfile(Set<String> completedCourses, BigDecimal outstandingBalance) { }

    private final PrerequisiteChecker prerequisites;
    private final FinancialHoldChecker finances;
    private final HoldRepository holds;
    private final Map<String, StudentProfile> students;

    public EligibilityService(PrerequisiteChecker prerequisites, FinancialHoldChecker finances,
                              HoldRepository holds, Map<String, StudentProfile> students) {
        this.prerequisites = prerequisites;
        this.finances = finances;
        this.holds = holds;
        this.students = students;
    }

    /** Checks the rules and persists an APPROVED hold. Returns the existing hold if one is already active. */
    public EnrollmentHold requestAndApprove(String studentId, String courseId) {
        Optional<EnrollmentHold> existing = holds.findActive(studentId, courseId);
        if (existing.isPresent()) {
            EnrollmentHold hold = existing.get();
            if (hold.status() == EnrollmentHold.Status.PENDING) {
                hold.approve();
                holds.save(hold);
            }
            return hold;
        }
        StudentProfile profile = students.get(studentId);
        if (profile == null) {
            throw new EligibilityException(Reason.UNKNOWN_STUDENT, "Unknown student " + studentId);
        }
        if (!prerequisites.knowsCourse(courseId)) {
            throw new EligibilityException(Reason.UNKNOWN_COURSE, "Unknown course " + courseId);
        }
        Set<String> missing = prerequisites.missing(courseId, profile.completedCourses());
        if (!missing.isEmpty()) {
            throw new EligibilityException(Reason.PREREQUISITE_NOT_MET,
                    studentId + " is missing prerequisites for " + courseId + ": " + missing);
        }
        if (finances.hasHold(profile.outstandingBalance())) {
            throw new EligibilityException(Reason.FINANCIAL_HOLD,
                    studentId + " has a financial hold (balance " + profile.outstandingBalance() + ")");
        }
        EnrollmentHold hold = EnrollmentHold.pending(holds.nextId(), studentId, courseId);
        holds.save(hold);          // PENDING is on disk first ...
        hold.approve();
        holds.save(hold);          // ... then APPROVED
        return hold;
    }

    /** Undo: revokes the hold. Does nothing if it is already revoked. */
    public EnrollmentHold revoke(String holdId) {
        EnrollmentHold hold = require(holdId);
        if (hold.status() != EnrollmentHold.Status.REVOKED) {
            hold.revoke();
            holds.save(hold);
        }
        return hold;
    }

    public Optional<EnrollmentHold> find(String holdId) {
        return holds.findById(holdId);
    }

    public Optional<EnrollmentHold> latestFor(String studentId, String courseId) {
        return holds.findLatest(studentId, courseId);
    }

    public List<EnrollmentHold> all() {
        return holds.findAll();
    }

    private EnrollmentHold require(String holdId) {
        return holds.findById(holdId)
                .orElseThrow(() -> new EligibilityException(Reason.HOLD_NOT_FOUND, "No hold " + holdId));
    }
}
