package edu.registration.finalization;

import edu.registration.DemoCatalog;
import edu.registration.finalization.FinalizationException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subsystem C on its own: no facade, no eligibility, no seating. CS201 is 4 credits: 4 * 150 + 20 = 620. */
class FinalizationTest {
    @TempDir
    Path dir;

    private FinalizationService service;

    @BeforeEach
    void setUp() {
        service = newService();
    }

    private FinalizationService newService() {
        DemoCatalog catalog = DemoCatalog.standard();
        return new FinalizationService(catalog.credits(),
                new TuitionCalculator(catalog.ratePerCredit(), catalog.registrationFee()),
                new FinalizationRepository(dir));
    }

    private FinalizationException failureOf(Runnable action) {
        return assertThrows(FinalizationException.class, action::run);
    }

    @Test
    void finalizingBillsTheStudentAndWritesTheTranscriptDurably() {
        FinalizationService.Receipt receipt = service.finalizeEnrollment("S001", "CS201");

        FinalizationService reopened = newService();
        Invoice invoice = reopened.findInvoice(receipt.invoice().id()).orElseThrow();
        TranscriptEntry entry = reopened.findEntry(receipt.entry().id()).orElseThrow();

        assertEquals(0, invoice.amount().compareTo(new BigDecimal("620")));
        assertEquals(Invoice.Status.ISSUED, invoice.status());
        assertEquals(TranscriptEntry.Status.ENROLLED, entry.status());
    }

    @Test
    void finalizingTwiceReturnsTheSameInvoiceAndEntry() {
        FinalizationService.Receipt first = service.finalizeEnrollment("S001", "CS201");
        FinalizationService.Receipt second = service.finalizeEnrollment("S001", "CS201");

        assertEquals(first.invoice().id(), second.invoice().id());
        assertEquals(first.entry().id(), second.entry().id());
    }

    @Test
    void whenTheGatewayIsDownNothingAtAllIsWritten() {
        service.setGatewayAvailable(false);

        assertEquals(Reason.GATEWAY_UNAVAILABLE, failureOf(() -> service.finalizeEnrollment("S001", "CS201")).reason());

        assertFalse(service.isGatewayAvailable());
        assertTrue(service.latestInvoiceFor("S001", "CS201").isEmpty());
        assertTrue(service.latestEntryFor("S001", "CS201").isEmpty());
    }

    @Test
    void anInvoiceThatSurvivedACrashIsReusedAndOnlyTheMissingEntryIsCreated() {
        FinalizationRepository repository = new FinalizationRepository(dir);
        repository.saveInvoice(Invoice.issue("INV-1", "S001", "CS201", new BigDecimal("620")));

        FinalizationService.Receipt receipt = service.finalizeEnrollment("S001", "CS201");

        assertEquals("INV-1", receipt.invoice().id());
        assertEquals(TranscriptEntry.Status.ENROLLED, receipt.entry().status());
    }

    @Test
    void unknownCoursesCanNotBeBilled() {
        assertEquals(Reason.UNKNOWN_COURSE, failureOf(() -> service.finalizeEnrollment("S001", "ZZZ")).reason());
    }

    @Test
    void cancellingVoidsTheInvoiceAndDropsTheEntryAndIsIdempotent() {
        FinalizationService.Receipt receipt = service.finalizeEnrollment("S001", "CS201");

        service.cancel(receipt.invoice().id(), receipt.entry().id());
        service.cancel(receipt.invoice().id(), receipt.entry().id());

        assertEquals(Invoice.Status.VOIDED, service.latestInvoiceFor("S001", "CS201").orElseThrow().status());
        assertEquals(TranscriptEntry.Status.DROPPED, service.latestEntryFor("S001", "CS201").orElseThrow().status());
    }

    @Test
    void cancellingWithNoIdsDoesNothingAndUnknownIdsAreErrors() {
        service.cancel(null, null);

        assertEquals(Reason.NOT_FOUND, failureOf(() -> service.cancel("INV-404", null)).reason());
        assertEquals(Reason.NOT_FOUND, failureOf(() -> service.cancel(null, "T-404")).reason());
    }

    @Test
    void anInvoiceFollowsItsLegalTransitions() {
        Invoice invoice = Invoice.issue("INV-1", "S001", "CS101", BigDecimal.TEN);
        invoice.markPaid();
        invoice.voidIt();

        assertFalse(invoice.isActive());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(invoice::markPaid).reason());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(invoice::voidIt).reason());
    }

    @Test
    void anIssuedInvoiceMayBeVoidedWithoutBeingPaid() {
        Invoice invoice = Invoice.issue("INV-1", "S001", "CS101", BigDecimal.TEN);
        invoice.voidIt();

        assertEquals(Invoice.Status.VOIDED, invoice.status());
    }

    @Test
    void aTranscriptEntryIsFinalOnceDroppedOrCompleted() {
        TranscriptEntry dropped = TranscriptEntry.enrolled("T-1", "S001", "CS101");
        dropped.drop();
        TranscriptEntry completed = TranscriptEntry.enrolled("T-2", "S001", "CS101");
        completed.complete();

        assertEquals(TranscriptEntry.Status.DROPPED, dropped.status());
        assertEquals(TranscriptEntry.Status.COMPLETED, completed.status());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(dropped::complete).reason());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(completed::drop).reason());
    }

    @Test
    void tuitionIsPerCreditPlusAFlatFee() {
        TuitionCalculator calculator = new TuitionCalculator(new BigDecimal("100"), new BigDecimal("10"));

        assertEquals(0, calculator.tuitionFor(3).compareTo(new BigDecimal("310")));
    }
}
