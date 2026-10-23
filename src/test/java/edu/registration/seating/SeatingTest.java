package edu.registration.seating;

import edu.registration.DemoCatalog;
import edu.registration.seating.SeatingException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

 
class SeatingTest {
    @TempDir
    Path dir;

    private SeatAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = newAllocator();
    }

    private SeatAllocator newAllocator() {
        SeatRepository repository = new SeatRepository(dir);
        return new SeatAllocator(DemoCatalog.standard().capacities(), new SeatPolicy(),
                new WaitlistManager(repository), repository);
    }

    private SeatingException failureOf(Runnable action) {
        return assertThrows(SeatingException.class, action::run);
    }

    @Test
    void reservedSeatsSurviveARestart() {
        SeatAssignment seat = allocator.reserve("S001", "CS201");
        allocator.confirm(seat.id());

        SeatAssignment reloaded = newAllocator().find(seat.id()).orElseThrow();

        assertEquals(SeatAssignment.Status.CONFIRMED, reloaded.status());
        assertEquals(0, newAllocator().remainingSeats("CS201"));
    }

    @Test
    void aFullSectionSendsTheStudentToTheWaitlistAndCreatesNoSeat() {
        allocator.reserve("S001", "CS201");

        SeatingException e = failureOf(() -> allocator.reserve("S004", "CS201"));

        assertEquals(Reason.SECTION_FULL, e.reason());
        assertEquals(1, e.waitlistPosition());
        assertEquals(List.of("S004"), allocator.waitlistOf("CS201"));
        assertTrue(allocator.latestFor("S004", "CS201").isEmpty());
    }

    @Test
    void waitingTwiceKeepsOnePlaceInLineAndNewcomersJoinAtTheBack() {
        allocator.reserve("S001", "CS201");

        assertEquals(1, failureOf(() -> allocator.reserve("S004", "CS201")).waitlistPosition());
        assertEquals(1, failureOf(() -> allocator.reserve("S004", "CS201")).waitlistPosition());
        assertEquals(2, failureOf(() -> allocator.reserve("S005", "CS201")).waitlistPosition());
        assertEquals(List.of("S004", "S005"), allocator.waitlistOf("CS201"));
    }

    @Test
    void aReleasedSeatCanBeTakenAndTakingItLeavesTheWaitlist() {
        SeatAssignment first = allocator.reserve("S001", "CS201");
        failureOf(() -> allocator.reserve("S004", "CS201"));

        allocator.release(first.id());
        SeatAssignment second = allocator.reserve("S004", "CS201");

        assertEquals("S004", second.studentId());
        assertTrue(allocator.waitlistOf("CS201").isEmpty());
    }

    @Test
    void reservingTwiceReturnsTheSameSeat() {
        SeatAssignment first = allocator.reserve("S001", "CS101");
        SeatAssignment second = allocator.reserve("S001", "CS101");

        assertEquals(first.id(), second.id());
        assertEquals(1, allocator.remainingSeats("CS101"));
    }

    @Test
    void confirmAndReleaseAreIdempotent() {
        SeatAssignment seat = allocator.reserve("S001", "CS101");

        assertEquals(SeatAssignment.Status.CONFIRMED, allocator.confirm(seat.id()).status());
        assertEquals(SeatAssignment.Status.CONFIRMED, allocator.confirm(seat.id()).status());
        assertEquals(SeatAssignment.Status.RELEASED, allocator.release(seat.id()).status());
        assertEquals(SeatAssignment.Status.RELEASED, allocator.release(seat.id()).status());
    }

    @Test
    void aReleasedSeatCanNotBeConfirmedAgain() {
        SeatAssignment seat = allocator.reserve("S001", "CS101");
        allocator.release(seat.id());

        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(() -> allocator.confirm(seat.id())).reason());
    }

    @Test
    void unknownCoursesAndSeatsAreErrors() {
        assertEquals(Reason.UNKNOWN_COURSE, failureOf(() -> allocator.reserve("S001", "ZZZ")).reason());
        assertEquals(Reason.UNKNOWN_COURSE, failureOf(() -> allocator.remainingSeats("ZZZ")).reason());
        assertEquals(Reason.SEAT_NOT_FOUND, failureOf(() -> allocator.confirm("S-404")).reason());
        assertEquals(Reason.SEAT_NOT_FOUND, failureOf(() -> allocator.release("S-404")).reason());
    }

    @Test
    void seatPolicyNeverReportsNegativeRoom() {
        SeatPolicy policy = new SeatPolicy();

        assertTrue(policy.hasFreeSeat(2, 1));
        assertFalse(policy.hasFreeSeat(2, 2));
        assertEquals(0, policy.remaining(2, 5));
        assertEquals(1, policy.remaining(2, 1));
    }

    @Test
    void aSeatPolicesItsOwnLifeCycle() {
        SeatAssignment seat = SeatAssignment.reserved("S-1", "S001", "CS101");
        seat.confirm();
        seat.release();

        assertFalse(seat.isActive());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(seat::confirm).reason());
        assertEquals(Reason.ILLEGAL_TRANSITION, failureOf(seat::release).reason());
    }

    @Test
    void aReservedSeatMayBeReleasedWithoutBeingConfirmed() {
        SeatAssignment seat = SeatAssignment.reserved("S-1", "S001", "CS101");
        seat.release();

        assertEquals(SeatAssignment.Status.RELEASED, seat.status());
    }
}
