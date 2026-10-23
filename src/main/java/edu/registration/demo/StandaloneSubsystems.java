package edu.registration.demo;

import edu.registration.DemoCatalog;
import edu.registration.eligibility.EligibilityException;
import edu.registration.eligibility.EligibilityService;
import edu.registration.eligibility.EnrollmentHold;
import edu.registration.eligibility.FinancialHoldChecker;
import edu.registration.eligibility.HoldRepository;
import edu.registration.eligibility.PrerequisiteChecker;
import edu.registration.finalization.FinalizationException;
import edu.registration.finalization.FinalizationRepository;
import edu.registration.finalization.FinalizationService;
import edu.registration.finalization.TuitionCalculator;
import edu.registration.seating.SeatAllocator;
import edu.registration.seating.SeatAssignment;
import edu.registration.seating.SeatPolicy;
import edu.registration.seating.SeatRepository;
import edu.registration.seating.SeatingException;
import edu.registration.seating.WaitlistManager;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shows that A, B and C each work completely on their own, with no facade and no other subsystem.
 * Run: mvn -q compile exec:java -Dexec.mainClass=edu.registration.demo.StandaloneSubsystems
 */
public final class StandaloneSubsystems {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("standalone-demo");
        DemoCatalog catalog = DemoCatalog.standard();

        System.out.println("--- Subsystem A alone ---");
        EligibilityService a = new EligibilityService(
                new PrerequisiteChecker(catalog.prerequisites()),
                new FinancialHoldChecker(catalog.financialHoldThreshold()),
                new HoldRepository(dir), catalog.students());
        EnrollmentHold hold = a.requestAndApprove("S001", "CS201");
        System.out.println("S001 -> CS201: hold " + hold.id() + " " + hold.status());
        try {
            a.requestAndApprove("S002", "CS201");
        } catch (EligibilityException e) {
            System.out.println("S002 -> CS201 refused: " + e.reason());
        }
        System.out.println("revoke: " + a.revoke(hold.id()).status());

        System.out.println("--- Subsystem B alone ---");
        SeatRepository seatRepo = new SeatRepository(dir);
        SeatAllocator b = new SeatAllocator(catalog.capacities(), new SeatPolicy(),
                new WaitlistManager(seatRepo), seatRepo);
        SeatAssignment seat = b.reserve("S001", "CS201");
        System.out.println("S001 reserved " + seat.id() + " " + seat.status());
        try {
            b.reserve("S004", "CS201");
        } catch (SeatingException e) {
            System.out.println("S004 refused: " + e.reason() + ", waitlist position " + e.waitlistPosition());
        }
        System.out.println("confirm: " + b.confirm(seat.id()).status() + ", release: " + b.release(seat.id()).status());

        System.out.println("--- Subsystem C alone ---");
        FinalizationService c = new FinalizationService(catalog.credits(),
                new TuitionCalculator(catalog.ratePerCredit(), catalog.registrationFee()),
                new FinalizationRepository(dir));
        FinalizationService.Receipt receipt = c.finalizeEnrollment("S001", "CS201");
        System.out.println("invoice " + receipt.invoice().id() + " " + receipt.invoice().amount()
                + ", transcript " + receipt.entry().status());
        c.setGatewayAvailable(false);
        try {
            c.finalizeEnrollment("S004", "CS201");
        } catch (FinalizationException e) {
            System.out.println("gateway down: " + e.reason());
        }
        c.cancel(receipt.invoice().id(), receipt.entry().id());
        System.out.println("cancelled: " + c.findInvoice(receipt.invoice().id()).orElseThrow().status());
    }
}
