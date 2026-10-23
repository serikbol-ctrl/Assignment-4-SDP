package edu.registration.seating;

import edu.registration.seating.SeatingException.Reason;

import java.util.List;
import java.util.Map;
import java.util.Optional;







public final class SeatAllocator {
    private final Map<String, Integer> capacities;
    private final SeatPolicy policy;
    private final WaitlistManager waitlist;
    private final SeatRepository seats;

    public SeatAllocator(Map<String, Integer> capacities, SeatPolicy policy,
                         WaitlistManager waitlist, SeatRepository seats) {
        this.capacities = capacities;
        this.policy = policy;
        this.waitlist = waitlist;
        this.seats = seats;
    }

    public SeatAssignment reserve(String studentId, String courseId) {
        Integer capacity = capacities.get(courseId);
        if (capacity == null) {
            throw new SeatingException(Reason.UNKNOWN_COURSE, "No section for course " + courseId);
        }
        Optional<SeatAssignment> existing = seats.findActive(studentId, courseId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!policy.hasFreeSeat(capacity, seats.countActive(courseId))) {
            int position = waitlist.join(courseId, studentId);
            throw new SeatingException(Reason.SECTION_FULL,
                    courseId + " is full; " + studentId + " is #" + position + " on the waitlist", position);
        }
        SeatAssignment seat = SeatAssignment.reserved(seats.nextId(), studentId, courseId);
        seats.save(seat);
        waitlist.leave(courseId, studentId);
        return seat;
    }

    public SeatAssignment confirm(String seatId) {
        SeatAssignment seat = require(seatId);
        if (seat.status() != SeatAssignment.Status.CONFIRMED) {
            seat.confirm();
            seats.save(seat);
        }
        return seat;
    }

     
    public SeatAssignment release(String seatId) {
        SeatAssignment seat = require(seatId);
        if (seat.status() != SeatAssignment.Status.RELEASED) {
            seat.release();
            seats.save(seat);
        }
        return seat;
    }

    public int remainingSeats(String courseId) {
        Integer capacity = capacities.get(courseId);
        if (capacity == null) {
            throw new SeatingException(Reason.UNKNOWN_COURSE, "No section for course " + courseId);
        }
        return policy.remaining(capacity, seats.countActive(courseId));
    }

    public List<String> waitlistOf(String courseId) {
        return waitlist.queue(courseId);
    }

    public Optional<SeatAssignment> find(String seatId) {
        return seats.findById(seatId);
    }

    public Optional<SeatAssignment> latestFor(String studentId, String courseId) {
        return seats.findLatest(studentId, courseId);
    }

    private SeatAssignment require(String seatId) {
        return seats.findById(seatId)
                .orElseThrow(() -> new SeatingException(Reason.SEAT_NOT_FOUND, "No seat " + seatId));
    }
}
