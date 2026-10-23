package edu.registration.seating;

/** Pure business rule: is there still room in a section? No state, no I/O. */
public final class SeatPolicy {
    public boolean hasFreeSeat(int capacity, long activeSeats) {
        return activeSeats < capacity;
    }

    public int remaining(int capacity, long activeSeats) {
        return (int) Math.max(0, capacity - activeSeats);
    }
}
