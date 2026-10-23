package edu.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrarSystemTest {
    @TempDir
    Path dir;

    @Test
    void resettingWipesEveryTableSoTheNextRunStartsClean() {
        RegistrarSystem first = RegistrarSystem.open(dir);
        first.enrollment().enroll("S001", "CS101");
        assertEquals(1, first.chains().findAll().size());

        RegistrarSystem.reset(dir);
        RegistrarSystem second = RegistrarSystem.open(dir);

        assertTrue(second.chains().findAll().isEmpty());
        assertTrue(second.eligibility().all().isEmpty());
        assertEquals(2, second.seating().remainingSeats("CS101"));
    }

    @Test
    void resettingADirectoryThatDoesNotExistIsHarmless() {
        RegistrarSystem.reset(dir.resolve("never-created"));
    }

    @Test
    void theSubsystemsAreAvailableOnTheirOwnWithoutTheFacade() {
        RegistrarSystem system = RegistrarSystem.open(dir);

        assertEquals("S001", system.eligibility().requestAndApprove("S001", "CS101").studentId());
        assertEquals("S001", system.seating().reserve("S001", "CS101").studentId());
        assertEquals("S001", system.finalization().finalizeEnrollment("S001", "CS101").invoice().studentId());
        assertTrue(system.chains().findAll().isEmpty());                    
    }
}
