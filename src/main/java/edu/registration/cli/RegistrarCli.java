package edu.registration.cli;

import edu.registration.RegistrarSystem;
import edu.registration.facade.ChainRecord;
import edu.registration.facade.ChainStatus;
import edu.registration.facade.EnrollmentFailedException;
import edu.registration.facade.EnrollmentReceipt;
import edu.registration.facade.InvalidRequestException;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;





public final class RegistrarCli {
    private final Path dataDir;
    private final Scanner in;
    private final PrintStream out;
    private RegistrarSystem system;

    public RegistrarCli(Path dataDir, InputStream in, PrintStream out) {
        this.dataDir = dataDir;
        this.in = new Scanner(in);
        this.out = out;
    }

    public static void main(String[] args) {
        Path dataDir = Path.of(System.getProperty("registrar.data", "data"));
        new RegistrarCli(dataDir, System.in, System.out).run();
    }

    public void run() {
        system = RegistrarSystem.open(dataDir);
        out.println("=== University Registration (Facade demo) ===");
        out.println("Data directory: " + dataDir.toAbsolutePath());
        warnAboutInterruptedChains();
        printHelp();
        while (true) {
            printMenu();
            String choice = readLine("> ");
            if (choice == null || choice.equals("0")) {
                out.println("Bye. Everything is on disk; start again to see it come back.");
                return;
            }
            try {
                handle(choice.trim());
            } catch (EnrollmentFailedException e) {
                out.println("!! " + e.getMessage());
                out.println("   failed step: " + e.failedStep() + "   undone: " + e.compensatedSteps()
                        + "   chain " + e.chainId() + " is now " + e.finalStatus());
            } catch (InvalidRequestException e) {
                out.println("!! Invalid request: " + e.getMessage());
            } catch (RuntimeException e) {
                out.println("!! Unexpected error: " + e);
            }
        }
    }

    private void handle(String choice) {
        switch (choice) {
            case "1" -> {
                String student = readLine("student id: ");
                String course = readLine("course id : ");
                EnrollmentReceipt r = system.enrollment().enroll(student, course);
                out.println("OK. Enrolled " + r.studentId() + " in " + r.courseId() + ". Chain " + r.chainId()
                        + ": hold " + r.holdId() + ", seat " + r.seatId() + ", invoice " + r.invoiceId()
                        + " (" + r.amount() + ")");
            }
            case "2" -> {
                String student = readLine("student id: ");
                String course = readLine("course id : ");
                List<String> undone = system.enrollment().drop(student, course);
                out.println("Dropped. Undone, in order: " + undone);
            }
            case "3" -> listChains();
            case "4" -> out.println(system.admin().inspect(readLine("chain id: ")).describe());
            case "5" -> {
                String id = readLine("chain id: ");
                out.println("Compensated manually. Undone, in order: " + system.admin().compensate(id));
            }
            case "6" -> {
                boolean nowUp = !system.admin().isBillingGatewayAvailable();
                system.admin().setBillingGatewayAvailable(nowUp);
                out.println("Billing gateway is now " + (nowUp ? "UP" : "DOWN (step C will fail)"));
            }
            case "7" -> {
                String course = readLine("course id: ");
                out.println(course + ": " + system.admin().remainingSeats(course) + " seat(s) left, waitlist "
                        + system.admin().waitlistOf(course));
            }
            case "8" -> {
                List<String> report = system.enrollment().resumeInterrupted();
                if (report.isEmpty()) {
                    out.println("Nothing to resume.");
                }
                report.forEach(line -> out.println(line));
            }
            case "9" -> {
                String student = readLine("student id: ");
                String course = readLine("course id : ");
                String point = readLine("crash after step A or B? ");
                ChainStatus crashAt = point.equalsIgnoreCase("A") ? ChainStatus.PENDING_B : ChainStatus.PENDING_C;
                String id = system.enrollment().enrollAndCrashAt(student, course, crashAt);
                out.println("Simulated crash. Chain " + id + " is left as " + crashAt
                        + " on disk. Quit and restart, or use option 8.");
            }
            case "h" -> printHelp();
            case "r" -> {
                if ("yes".equalsIgnoreCase(readLine("Delete ALL persisted data? type yes: "))) {
                    RegistrarSystem.reset(dataDir);
                    system = RegistrarSystem.open(dataDir);
                    out.println("Store reset.");
                }
            }
            default -> out.println("Unknown option.");
        }
    }

    private void listChains() {
        List<ChainRecord> all = system.admin().listChains();
        if (all.isEmpty()) {
            out.println("(no chains yet)");
        }
        for (ChainRecord c : all) {
            out.printf("%-5s %-5s %-8s %-13s hold=%s seat=%s invoice=%s%n",
                    c.id(), c.studentId(), c.courseId(), c.status(),
                    c.holdId(), c.seatId(), c.invoiceId());
        }
    }

    private void warnAboutInterruptedChains() {
        List<ChainRecord> interrupted = system.admin().findInterrupted();
        if (!interrupted.isEmpty()) {
            out.println();
            out.println("!! " + interrupted.size() + " interrupted chain(s) found on disk:");
            interrupted.forEach(c -> out.println("   " + c.id() + " " + c.studentId() + " -> " + c.courseId()
                    + " is " + c.status()));
            out.println("   Inspect them (4) or finish them (8).");
        }
    }

    private void printMenu() {
        out.println();
        out.println("1 enroll   2 drop   3 list chains   4 inspect chain   5 compensate chain by hand");
        out.println("6 toggle billing gateway   7 seats/waitlist   8 resume interrupted chains");
        out.println("9 simulate a crash mid-chain   h help   r reset store   0 quit");
    }

    private void printHelp() {
        out.println();
        out.println("Students: S001 (done CS101)  S002 (nothing done)  S003 (owes 750)  S004 (done CS101)  S005 (done CS101, CS201)");
        out.println("Courses : CS101 (2 seats)  CS201 (1 seat, needs CS101)  CS301 (5 seats, needs CS201)  MATH101 (30 seats)");
        out.println("Try:  rejected at A    -> S002 in CS201 (prerequisite) or S003 in CS201 (financial hold)");
        out.println("      B fails, undo A  -> S001 in CS201, then S004 in CS201 (section full)");
        out.println("      C fails, undo B,A-> option 6 (gateway DOWN), then S005 in MATH101");
    }

    private String readLine(String prompt) {
        out.print(prompt);
        out.flush();
        return in.hasNextLine() ? in.nextLine() : null;
    }
}
