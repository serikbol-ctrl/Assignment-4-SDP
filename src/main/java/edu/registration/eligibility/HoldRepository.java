package edu.registration.eligibility;

import edu.registration.storage.TsvTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

 
public final class HoldRepository {
    private final TsvTable table;

    public HoldRepository(Path dataDir) {
        this.table = new TsvTable(dataDir.resolve("eligibility_holds.tsv"));
    }

    public String nextId() {
        return "H-" + (table.readAll().size() + 1);
    }

    public void save(EnrollmentHold hold) {
        table.upsert(new String[]{hold.id(), hold.studentId(), hold.courseId(), hold.status().name()});
    }

    public Optional<EnrollmentHold> findById(String id) {
        return findAll().stream().filter(h -> h.id().equals(id)).findFirst();
    }

     
    public Optional<EnrollmentHold> findActive(String studentId, String courseId) {
        return findAll().stream()
                .filter(h -> h.studentId().equals(studentId) && h.courseId().equals(courseId) && h.isActive())
                .findFirst();
    }

     
    public Optional<EnrollmentHold> findLatest(String studentId, String courseId) {
        EnrollmentHold latest = null;
        for (EnrollmentHold h : findAll()) {
            if (h.studentId().equals(studentId) && h.courseId().equals(courseId)) {
                latest = h;
            }
        }
        return Optional.ofNullable(latest);
    }

    public List<EnrollmentHold> findAll() {
        List<EnrollmentHold> holds = new ArrayList<>();
        for (String[] r : table.readAll()) {
            holds.add(new EnrollmentHold(r[0], r[1], r[2], EnrollmentHold.Status.valueOf(r[3])));
        }
        return holds;
    }
}
