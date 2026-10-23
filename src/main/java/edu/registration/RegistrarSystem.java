package edu.registration;

import edu.registration.eligibility.EligibilityService;
import edu.registration.eligibility.FinancialHoldChecker;
import edu.registration.eligibility.HoldRepository;
import edu.registration.eligibility.PrerequisiteChecker;
import edu.registration.facade.ChainRepository;
import edu.registration.facade.EnrollmentFacade;
import edu.registration.facade.RegistrarAdminFacade;
import edu.registration.finalization.FinalizationRepository;
import edu.registration.finalization.FinalizationService;
import edu.registration.finalization.TuitionCalculator;
import edu.registration.seating.SeatAllocator;
import edu.registration.seating.SeatPolicy;
import edu.registration.seating.SeatRepository;
import edu.registration.seating.WaitlistManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Composition root: the only place that knows all three subsystems and wires them to the facades.
 * Opening a system over an existing data directory is exactly what "restarting the application" means:
 * all objects are new, all state comes back from disk.
 */
public final class RegistrarSystem {
    private final EligibilityService eligibility;
    private final SeatAllocator seating;
    private final FinalizationService finalization;
    private final ChainRepository chains;
    private final EnrollmentFacade enrollment;
    private final RegistrarAdminFacade admin;

    private RegistrarSystem(EligibilityService eligibility, SeatAllocator seating, FinalizationService finalization,
                            ChainRepository chains) {
        this.eligibility = eligibility;
        this.seating = seating;
        this.finalization = finalization;
        this.chains = chains;
        this.enrollment = new EnrollmentFacade(eligibility, seating, finalization, chains);
        this.admin = new RegistrarAdminFacade(eligibility, seating, finalization, chains);
    }

    public static RegistrarSystem open(Path dataDir) {
        return open(dataDir, DemoCatalog.standard());
    }

    public static RegistrarSystem open(Path dataDir, DemoCatalog catalog) {
        EligibilityService eligibility = new EligibilityService(
                new PrerequisiteChecker(catalog.prerequisites()),
                new FinancialHoldChecker(catalog.financialHoldThreshold()),
                new HoldRepository(dataDir),
                catalog.students());

        SeatRepository seatRepository = new SeatRepository(dataDir);
        SeatAllocator seating = new SeatAllocator(catalog.capacities(), new SeatPolicy(),
                new WaitlistManager(seatRepository), seatRepository);

        FinalizationService finalization = new FinalizationService(catalog.credits(),
                new TuitionCalculator(catalog.ratePerCredit(), catalog.registrationFee()),
                new FinalizationRepository(dataDir));

        return new RegistrarSystem(eligibility, seating, finalization, new ChainRepository(dataDir));
    }

    /** Deletes every table file in the data directory (a clean slate for a fresh run). */
    public static void reset(Path dataDir) {
        if (!Files.isDirectory(dataDir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dataDir, "*.tsv*")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public EnrollmentFacade enrollment() { return enrollment; }
    public RegistrarAdminFacade admin() { return admin; }

    // Direct access to the subsystems: for standalone use and for tests. Clients use the facades.
    public EligibilityService eligibility() { return eligibility; }
    public SeatAllocator seating() { return seating; }
    public FinalizationService finalization() { return finalization; }
    public ChainRepository chains() { return chains; }
}
