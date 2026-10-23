package edu.registration.facade;

import edu.registration.storage.TsvTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Persists the progress of every chain. This is the facade's own table, separate from A, B and C. */
public final class ChainRepository {
    private final TsvTable table;

    public ChainRepository(Path dataDir) {
        this.table = new TsvTable(dataDir.resolve("facade_chains.tsv"));
    }

    public String nextId() {
        return "C-" + (table.readAll().size() + 1);
    }

    public void save(ChainRecord c) {
        table.upsert(new String[]{c.id(), c.studentId(), c.courseId(), c.status().name(),
                c.holdId(), c.seatId(), c.invoiceId(), c.entryId(), c.note()});
    }

    public Optional<ChainRecord> findById(String id) {
        return findAll().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public List<ChainRecord> findAll() {
        List<ChainRecord> all = new ArrayList<>();
        for (String[] r : table.readAll()) {
            all.add(new ChainRecord(r[0], r[1], r[2], ChainStatus.valueOf(r[3]), r[4], r[5], r[6], r[7], r[8]));
        }
        return all;
    }

    public List<ChainRecord> findInterrupted() {
        return findAll().stream().filter(c -> c.status().isInterrupted()).toList();
    }

    public Optional<ChainRecord> findCompleted(String studentId, String courseId) {
        return findAll().stream()
                .filter(c -> c.status() == ChainStatus.COMPLETED
                        && c.studentId().equals(studentId) && c.courseId().equals(courseId))
                .findFirst();
    }

    @Override
    public String toString() {
        return "ChainRepository" + Arrays.toString(findAll().stream().map(ChainRecord::id).toArray());
    }
}
