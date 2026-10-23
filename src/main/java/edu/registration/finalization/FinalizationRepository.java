package edu.registration.finalization;

import edu.registration.storage.TsvTable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

 
public final class FinalizationRepository {
    private final TsvTable invoices;
    private final TsvTable transcript;

    public FinalizationRepository(Path dataDir) {
        this.invoices = new TsvTable(dataDir.resolve("finalization_invoices.tsv"));
        this.transcript = new TsvTable(dataDir.resolve("finalization_transcript.tsv"));
    }

    public String nextInvoiceId() {
        return "INV-" + (invoices.readAll().size() + 1);
    }

    public String nextEntryId() {
        return "T-" + (transcript.readAll().size() + 1);
    }

    public void saveInvoice(Invoice i) {
        invoices.upsert(new String[]{i.id(), i.studentId(), i.courseId(), i.amount().toPlainString(), i.status().name()});
    }

    public void saveEntry(TranscriptEntry e) {
        transcript.upsert(new String[]{e.id(), e.studentId(), e.courseId(), e.status().name()});
    }

    public List<Invoice> allInvoices() {
        List<Invoice> all = new ArrayList<>();
        for (String[] r : invoices.readAll()) {
            all.add(new Invoice(r[0], r[1], r[2], new BigDecimal(r[3]), Invoice.Status.valueOf(r[4])));
        }
        return all;
    }

    public List<TranscriptEntry> allEntries() {
        List<TranscriptEntry> all = new ArrayList<>();
        for (String[] r : transcript.readAll()) {
            all.add(new TranscriptEntry(r[0], r[1], r[2], TranscriptEntry.Status.valueOf(r[3])));
        }
        return all;
    }

    public Optional<Invoice> findInvoice(String id) {
        return allInvoices().stream().filter(i -> i.id().equals(id)).findFirst();
    }

    public Optional<TranscriptEntry> findEntry(String id) {
        return allEntries().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public Optional<Invoice> findActiveInvoice(String studentId, String courseId) {
        return allInvoices().stream()
                .filter(i -> i.studentId().equals(studentId) && i.courseId().equals(courseId) && i.isActive())
                .findFirst();
    }

    public Optional<TranscriptEntry> findActiveEntry(String studentId, String courseId) {
        return allEntries().stream()
                .filter(e -> e.studentId().equals(studentId) && e.courseId().equals(courseId) && e.isActive())
                .findFirst();
    }

    public Optional<Invoice> findLatestInvoice(String studentId, String courseId) {
        Invoice latest = null;
        for (Invoice i : allInvoices()) {
            if (i.studentId().equals(studentId) && i.courseId().equals(courseId)) {
                latest = i;
            }
        }
        return Optional.ofNullable(latest);
    }

    public Optional<TranscriptEntry> findLatestEntry(String studentId, String courseId) {
        TranscriptEntry latest = null;
        for (TranscriptEntry e : allEntries()) {
            if (e.studentId().equals(studentId) && e.courseId().equals(courseId)) {
                latest = e;
            }
        }
        return Optional.ofNullable(latest);
    }
}
