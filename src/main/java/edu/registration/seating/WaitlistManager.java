package edu.registration.seating;

import java.util.List;

/** Decides how students queue for a full section. Joining twice never creates a second entry. */
public final class WaitlistManager {
    private final SeatRepository repository;

    public WaitlistManager(SeatRepository repository) {
        this.repository = repository;
    }

    /** Puts the student at the back of the queue (if not already queued). Returns the 1-based position. */
    public int join(String courseId, String studentId) {
        List<String> queue = repository.waitlist(courseId);
        int index = queue.indexOf(studentId);
        if (index >= 0) {
            return index + 1;
        }
        repository.addToWaitlist(courseId, studentId);
        return queue.size() + 1;
    }

    public void leave(String courseId, String studentId) {
        repository.removeFromWaitlist(courseId, studentId);
    }

    public List<String> queue(String courseId) {
        return repository.waitlist(courseId);
    }
}
