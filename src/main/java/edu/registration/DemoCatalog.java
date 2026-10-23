package edu.registration;

import edu.registration.eligibility.EligibilityService.StudentProfile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

 
public record DemoCatalog(Map<String, StudentProfile> students,
                          Map<String, Set<String>> prerequisites,
                          Map<String, Integer> capacities,
                          Map<String, Integer> credits,
                          BigDecimal financialHoldThreshold,
                          BigDecimal ratePerCredit,
                          BigDecimal registrationFee) {

    public static DemoCatalog standard() {
        return new DemoCatalog(
                Map.of(
                        "S001", new StudentProfile(Set.of("CS101"), BigDecimal.ZERO),                     
                        "S002", new StudentProfile(Set.of(), BigDecimal.ZERO),                            
                        "S003", new StudentProfile(Set.of("CS101"), new BigDecimal("750")),               
                        "S004", new StudentProfile(Set.of("CS101"), BigDecimal.ZERO),                     
                        "S005", new StudentProfile(Set.of("CS101", "CS201"), BigDecimal.ZERO)),           
                Map.of(
                        "CS101", Set.of(),
                        "CS201", Set.of("CS101"),
                        "CS301", Set.of("CS201"),
                        "MATH101", Set.of()),
                Map.of("CS101", 2, "CS201", 1, "CS301", 5, "MATH101", 30),
                Map.of("CS101", 3, "CS201", 4, "CS301", 4, "MATH101", 3),
                new BigDecimal("500"),
                new BigDecimal("150"),
                new BigDecimal("20"));
    }
}
