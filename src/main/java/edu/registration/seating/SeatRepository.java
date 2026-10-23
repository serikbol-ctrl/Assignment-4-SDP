package edu.registration.seating;

import edu.registration.storage.TsvTable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

 
public final class SeatRepository {
    private final TsvTable seats;
    private final TsvTable waitlists;

    public SeatRepository(Path dataDir) {
        this.seats = new TsvTable(dataDir.resolve("seating_assignments.tsv"));
        this.waitlists = new TsvTable(dataDir.resolve("seating_waitlist.tsv"));
    }

    public String nextId() {
        return "S-" + (seats.readAll().size() + 1);
    }

    public void save(SeatAssignment seat) {
        seats.upsert(new String[]{seat.id(), seat.studentId(), seat.courseId(), seat.status().name()});
    }

    public Optional<SeatAssignment> findById(String id) {
        return findAll().stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public Optional<SeatAssignment> findActive(String studentId, String courseId) {
        return findAll().stream()
                .filter(s -> s.studentId().equals(studentId) && s.courseId().equals(courseId) && s.isActive())
                .findFirst();
    }

    public Optional<SeatAssignment> findLatest(String studentId, String courseId) {
        SeatAssignment latest = null;
        for (SeatAssignment s : findAll()) {
            if (s.studentId().equals(studentId) && s.courseId().equals(courseId)) {
                latest = s;
            }
        }
        return Optional.ofNullable(latest);
    }

    public long countActive(String courseId) {
        return findAll().stream().filter(s -> s.courseId().equals(courseId) && s.isActive()).count();
    }

    public List<SeatAssignment> findAll() {
        List<SeatAssignment> all = new ArrayList<>();
        for (String[] r : seats.readAll()) {
            all.add(new SeatAssignment(r[0], r[1], r[2], SeatAssignment.Status.valueOf(r[3])));
        }
        return all;
    }

     
    public List<String> waitlist(String courseId) {
        List<String> queue = new ArrayList<>();
        for (String[] r : waitlists.readAll()) {
            if (r[0].equals(courseId)) {
                queue.add(r[1]);
            }
        }
        return queue;
    }

    public void addToWaitlist(String courseId, String studentId) {
        List<String[]> rows = waitlists.readAll();
        rows.add(new String[]{courseId, studentId});
        waitlists.writeAll(rows);
    }

    public void removeFromWaitlist(String courseId, String studentId) {
        List<String[]> rows = waitlists.readAll();
        rows.removeIf(r -> r[0].equals(courseId) && r[1].equals(studentId));
        waitlists.writeAll(rows);
    }
}
