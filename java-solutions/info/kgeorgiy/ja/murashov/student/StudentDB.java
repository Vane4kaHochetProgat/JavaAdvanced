package info.kgeorgiy.ja.murashov.student;

import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.GroupQuery;
import info.kgeorgiy.java.advanced.student.Student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements GroupQuery {

    private static final Comparator<Student> STUDENT_COMPARATOR = Comparator
            .comparing(Student::getLastName)
            .thenComparing(Student::getFirstName)
            .reversed()
            .thenComparing(Student::getId);

    //StudentQuery

    // getStudentsByRule functions
    private <E> List<E> getStudentsByRule(List<Student> students, final Function<Student, E> function) {
        return students.stream().map(function).collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return getStudentsByRule(students, Student::getFirstName);
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return getStudentsByRule(students, Student::getLastName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return getStudentsByRule(students, Student::getGroup);

    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return getStudentsByRule(students, x -> x.getFirstName() + " " + x.getLastName());
    }

    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return students.stream()
                .map(Student::getFirstName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return students.stream().max(Comparator.comparing(Student::getId))
                .map(Student::getFirstName).orElse("");
    }

    //sortStudentsBy functions

    private List<Student> sortStudentsBy(Collection<Student> students, Comparator<Student> SortComparator) {
        return students.stream().sorted(SortComparator).collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudentsBy(students, Comparator.comparing(Student::getId));
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudentsBy(students, STUDENT_COMPARATOR);
    }

    //findStudentsFilteredBy functions

    private List<Student> findStudentsFilteredBy(Collection<Student> students, final Predicate<Student> predicate) {
        return students.stream().filter(predicate).sorted(STUDENT_COMPARATOR).collect(Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsFilteredBy(students, x -> x.getFirstName().equals(name));
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsFilteredBy(students, x -> x.getLastName().equals(name));
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsFilteredBy(students, x -> x.getGroup().equals(group));
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return students.stream()
                .filter(x -> x.getGroup().equals(group))
                .collect(Collectors.toMap(
                        Student::getLastName,
                        Student::getFirstName,
                        BinaryOperator.minBy(Comparator.naturalOrder()))
                );
    }

    // GroupQuery

    private <E> Stream<Map.Entry<GroupName, E>> GroupCollector(Collection<Student> students, Collector<Student, ?, Map<GroupName, E>> groupingCollector) {
        return students.stream().collect(groupingCollector).entrySet().stream();
    }

    private List<Group> getGroupsBy(Collection<Student> students, Comparator<Student> GroupSortComparator) {
        return GroupCollector(students, Collectors.groupingBy(Student::getGroup))
                .map(x -> new Group(x.getKey(), x.getValue().stream().sorted(GroupSortComparator).toList()))
                .sorted(Comparator.comparing(Group::getName))
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroupsBy(students, STUDENT_COMPARATOR);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroupsBy(students, Comparator.comparing(Student::getId));
    }

    private GroupName getLargestGroupBy(Collection<Student> students, Comparator<Map.Entry<GroupName, List<Student>>> groupOrderComparator, Comparator<Map.Entry<GroupName, List<Student>>> maxGroupComparator) {
        return GroupCollector(students, Collectors.groupingBy(Student::getGroup, TreeMap::new, Collectors.toList()))
                .sorted(groupOrderComparator)
                .max(maxGroupComparator).map(Map.Entry::getKey).orElse(null);
    }

    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargestGroupBy(students, Map.Entry.comparingByKey(Comparator.comparing(GroupName::name).reversed()), Comparator.comparingInt(
                (Map.Entry<GroupName, List<Student>> group) -> group.getValue().size())
        );
    }


    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargestGroupBy(students, Map.Entry.comparingByKey(Comparator.comparing(GroupName::name)), Comparator.comparingInt(
                (Map.Entry<GroupName, List<Student>> group) -> getDistinctFirstNames(group.getValue()).size())
        );
    }

}
