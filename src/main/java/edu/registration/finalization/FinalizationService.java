package edu.registration.finalization;

import edu.registration.finalization.FinalizationException.Reason;

import java.util.Map;
import java.util.Optional;







public final class FinalizationService {
     
    public record Receipt(Invoice invoice, TranscriptEntry entry) { }

    private final Map<String, Integer> courseCredits;
    private final TuitionCalculator calculator;
    private final FinalizationRepository repository;
    private volatile boolean gatewayAvailable = true;

    public FinalizationService(Map<String, Integer> courseCredits, TuitionCalculator calculator,
                               FinalizationRepository repository) {
        this.courseCredits = courseCredits;
        this.calculator = calculator;
        this.repository = repository;
    }

    public Receipt finalizeEnrollment(String studentId, String courseId) {
        Integer credits = courseCredits.get(courseId);
        if (credits == null) {
            throw new FinalizationException(Reason.UNKNOWN_COURSE, "No tuition defined for course " + courseId);
        }
        Optional<Invoice> existingInvoice = repository.findActiveInvoice(studentId, courseId);
        Optional<TranscriptEntry> existingEntry = repository.findActiveEntry(studentId, courseId);
        if (existingInvoice.isPresent() && existingEntry.isPresent()) {
            return new Receipt(existingInvoice.get(), existingEntry.get());
        }
        if (!gatewayAvailable) {
            throw new FinalizationException(Reason.GATEWAY_UNAVAILABLE,
                    "Payment gateway is unavailable; nothing was billed");
        }
        Invoice invoice = existingInvoice.orElseGet(() -> {
            Invoice created = Invoice.issue(repository.nextInvoiceId(), studentId, courseId,
                    calculator.tuitionFor(credits));
            repository.saveInvoice(created);
            return created;
        });
        TranscriptEntry entry = existingEntry.orElseGet(() -> {
            TranscriptEntry created = TranscriptEntry.enrolled(repository.nextEntryId(), studentId, courseId);
            repository.saveEntry(created);
            return created;
        });
        return new Receipt(invoice, entry);
    }

     
    public void cancel(String invoiceId, String entryId) {
        if (invoiceId != null) {
            Invoice invoice = repository.findInvoice(invoiceId)
                    .orElseThrow(() -> new FinalizationException(Reason.NOT_FOUND, "No invoice " + invoiceId));
            if (invoice.status() != Invoice.Status.VOIDED) {
                invoice.voidIt();
                repository.saveInvoice(invoice);
            }
        }
        if (entryId != null) {
            TranscriptEntry entry = repository.findEntry(entryId)
                    .orElseThrow(() -> new FinalizationException(Reason.NOT_FOUND, "No transcript entry " + entryId));
            if (entry.status() != TranscriptEntry.Status.DROPPED) {
                entry.drop();
                repository.saveEntry(entry);
            }
        }
    }

    public void setGatewayAvailable(boolean available) {
        this.gatewayAvailable = available;
    }

    public boolean isGatewayAvailable() {
        return gatewayAvailable;
    }

    public Optional<Invoice> findInvoice(String invoiceId) {
        return repository.findInvoice(invoiceId);
    }

    public Optional<TranscriptEntry> findEntry(String entryId) {
        return repository.findEntry(entryId);
    }

    public Optional<Invoice> latestInvoiceFor(String studentId, String courseId) {
        return repository.findLatestInvoice(studentId, courseId);
    }

    public Optional<TranscriptEntry> latestEntryFor(String studentId, String courseId) {
        return repository.findLatestEntry(studentId, courseId);
    }
}
