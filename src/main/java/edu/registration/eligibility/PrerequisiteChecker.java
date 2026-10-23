package edu.registration.eligibility;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

 
public final class PrerequisiteChecker {
    private final Map<String, Set<String>> prerequisitesByCourse;

    public PrerequisiteChecker(Map<String, Set<String>> prerequisitesByCourse) {
        this.prerequisitesByCourse = prerequisitesByCourse;
    }

    public boolean knowsCourse(String courseId) {
        return prerequisitesByCourse.containsKey(courseId);
    }

    public Set<String> missing(String courseId, Set<String> completedCourses) {
        Set<String> missing = new TreeSet<>(prerequisitesByCourse.getOrDefault(courseId, Set.of()));
        missing.removeAll(completedCourses);
        return missing;
    }
}
