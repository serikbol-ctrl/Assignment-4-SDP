# University Registration: a Facade over a three-stage chain

Assignment 4 (Facade pattern), topic 7: **University Registration**.
One call, `enroll(student, course)`, runs a chain of three independent subsystems and, if a later step fails, undoes the earlier ones.

```
A  Eligibility (intake)       prerequisites + financial hold  -> an APPROVED hold
B  Seating (processing)       capacity + waitlist             -> a seat
C  Finalization (confirm)     tuition invoice + transcript    -> invoice and transcript entry
```

## Build, run, reset

Requirements: JDK 17+ and Maven 3.8+. There are **no runtime dependencies**.

| What | Command |
|---|---|
| Run the console demo | `mvn -q compile exec:java` |
| Run all tests + coverage report | `mvn test` (report: `target/site/jacoco/index.html`) |
| Show A, B, C working with no facade | `mvn -q compile exec:java -Dexec.mainClass=edu.registration.demo.StandaloneSubsystems` |
| Use another data folder | add `-Dregistrar.data=/some/folder` |
| **Reset the persisted store** | menu option `r` in the demo, or delete the `data/` folder |

State lives in tab-separated files in `data/` (an allowed "plain file" store). Every write goes to a temp file and is atomically moved over the real one, so a crash cannot leave half a table.
Each subsystem has its own files and its own repository class; the facade has its own table too (`facade_chains.tsv`).

## Architecture

![class diagram](docs/uml.png)

Vector version: `docs/uml.svg`.

| Package | Role |
|---|---|
| `eligibility` (A) | `EligibilityService`, `PrerequisiteChecker` + `FinancialHoldChecker` (pure rules), `EnrollmentHold` (entity), `HoldRepository` (own persistence), `EligibilityException` |
| `seating` (B) | `SeatAllocator`, `SeatPolicy` (pure rule), `WaitlistManager`, `SeatAssignment` (entity), `SeatRepository`, `SeatingException` |
| `finalization` (C) | `FinalizationService`, `TuitionCalculator` (pure rule), `Invoice` + `TranscriptEntry` (entities), `FinalizationRepository`, `FinalizationException` |
| `facade` | `EnrollmentFacade` (the workflow), `RegistrarAdminFacade` (inspection/ops), `ChainCompensator`, `ChainRepository`, `ChainRecord`, `ChainStatus`, `EnrollmentReceipt`, `EnrollmentFailedException`, `InvalidRequestException` |
| `storage` | `TsvTable`: the shared file-table utility (infrastructure only) |
| root | `RegistrarSystem` (composition root), `DemoCatalog` (reference data) |
| `cli`, `demo` | console menu and the "subsystems alone" demo |

A, B and C **never import each other**. Each one works completely on its own (see `StandaloneSubsystems` and the per-subsystem tests). Only the `facade` package knows all three.

Every stateful entity polices its own life cycle and throws on an illegal move:

| Entity | Statuses |
|---|---|
| `EnrollmentHold` | PENDING -> APPROVED -> REVOKED (or PENDING -> REVOKED) |
| `SeatAssignment` | RESERVED -> CONFIRMED -> RELEASED (or RESERVED -> RELEASED) |
| `Invoice` | ISSUED -> PAID -> VOIDED (or ISSUED -> VOIDED) |
| `TranscriptEntry` | ENROLLED -> DROPPED or COMPLETED |

## The chain, its failures and its persisted progress

`EnrollmentFacade.enroll` writes a row to `facade_chains.tsv` and updates it after every step:

`STARTED` -> `PENDING_B` (A done) -> `PENDING_C` (A, B done) -> `COMPLETED`.
Failure paths: `REJECTED` (refused at A, nothing to undo), `COMPENSATING` -> `COMPENSATED`, and `DROPPED` (completed enrollment withdrawn).

| Failure | How to provoke | What the facade undoes |
|---|---|---|
| Refused at A | `S002` in `CS201` (prerequisite), `S003` in `CS201` (financial hold) | nothing (`REJECTED`) |
| **B fails after A** | `S001` takes the only `CS201` seat, then `S004` asks for it | **A only** (hold REVOKED); `S004` goes on the waitlist |
| **C fails after A and B** | menu 6 (gateway DOWN), then `S005` in `MATH101` | **B, then A** (seat RELEASED, hold REVOKED) |
| Bad input | blank student/course, already enrolled | nothing written at all (`InvalidRequestException`) |

How deep the undo goes is decided from the chain row: a step is undone only if the row holds an id that step produced. Every undo and every forward step is idempotent, so a half-finished run can simply be run again.

**Restart behaviour.** On start the demo lists interrupted chains. `EnrollmentFacade.resumeInterrupted()` reads them from disk and: `STARTED / PENDING_B / PENDING_C` are driven forward from where they stopped; `COMPENSATING` finishes its undo. If a resumed step fails (say billing is still down), the normal compensation runs from whatever is on disk.
To see it live: menu `9` (simulate a crash after A or B), quit, start again, inspect (`4`), then resume (`8`).

## Principle of Least Knowledge: before and after

**Before** (the client talks to every subsystem itself and must know the order, the ids and the undo rules):

```java
EnrollmentHold hold = eligibility.requestAndApprove(student, course);
SeatAssignment seat;
try {
    seat = seating.reserve(student, course);
} catch (SeatingException e) {
    eligibility.revoke(hold.id());                     // client must remember to undo A
    throw e;
}
try {
    FinalizationService.Receipt r = finalization.finalizeEnrollment(student, course);
    seating.confirm(seat.id());
} catch (FinalizationException e) {
    seating.release(seat.id());                        // ... and the right order of undo
    eligibility.revoke(hold.id());
    throw e;
}
// and nothing here survives a crash, and the client knows 3 subsystems, 3 exception types, 5 ids
```

**After** (the client knows one object and one exception):

```java
try {
    EnrollmentReceipt receipt = facade.enroll(student, course);
} catch (EnrollmentFailedException e) {
    // e.failedStep(), e.compensatedSteps(), e.finalStatus(): everything already undone
}
```

## Why Facade and not Adapter

An Adapter exists to make one existing interface look like a different interface that a client already expects: the intent is *translation*, usually one adapted object. Here the three subsystems already have clean, usable interfaces; nothing needs translating. What the client lacks is a single simple entry point to a *workflow* that spans several subsystems: ordering, shared failure handling, compensation and progress tracking. That is the Facade intent: hide a complex subsystem group behind one simplified interface (and reduce what the client must know). The facade here also owns real logic (sequencing, persistence of progress, compensation), but it adds no new behaviour to A, B or C themselves; they stay independently usable, which an adapter-based design would not require or demonstrate. The number of classes is irrelevant to the choice; the intent is.

## Tests

`mvn test` runs 78 JUnit 5 tests:

* per subsystem, **with no facade**: `EligibilityTest`, `SeatingTest`, `FinalizationTest`, plus `TsvTableTest`;
* facade: `EnrollmentFacadeTest` (happy path, A rejection, both compensation depths and their order), `PersistenceReloadTest` (restart mid-chain, resume forward, resume then compensate, interrupted and half-done undo), `AdminFacadeTest`, `RegistrarSystemTest`;
* a small domain DSL, `UniversityDsl`, so tests read like sentences:

```java
uni.billingGatewayGoesDown();
EnrollmentFailedException failure = uni.student("S005").failsToEnrollIn("MATH101");
assertEquals(List.of("B", "A"), failure.compensatedSteps());
uni.expectSeat("S005", "MATH101", SeatAssignment.Status.RELEASED);
uni.restarts();            // new objects, same files on disk
```

F.I.R.S.T.: tests are Fast (milliseconds, local files), Independent (each gets its own `@TempDir`), Repeatable (no clock, network or shared state), Self-validating (assertions only), Timely (commit each test together with the code it checks).
Coverage is collected by JaCoCo during `mvn test` (the `cli` and `demo` packages are excluded).

## Known limits (be ready to say them)

* Files, not a database: simple and dependency-free; each write rewrites the whole table, which is fine for a demo, not for scale.
* One writer at a time: no cross-process locking.
* Waitlisted students are not promoted automatically when a seat frees up (an easy extension: call `WaitlistManager` from `release`).
* A crash *between* a subsystem committing and the chain row being updated is healed by idempotence: the resumed step finds its own earlier result by (student, course) instead of creating a duplicate.
