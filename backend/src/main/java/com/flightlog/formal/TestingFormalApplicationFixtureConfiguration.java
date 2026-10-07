package com.flightlog.formal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.data.StoredRecordRepository;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("testing")
public class TestingFormalApplicationFixtureConfiguration {
    public static final UUID PROJECT_ID = UUID.fromString("03a00000-0000-4000-8000-000000000001");
    private static final List<String> STUDENT_USERNAMES = List.of("student01", "student02", "student03");

    @Bean
    @Order(2)
    ApplicationRunner seedTestingFormalApplicationFixture(UserAccountRepository users, RecordStore store,
            StoredRecordRepository records) {
        return args -> seed(users, store, records);
    }

    private void seed(UserAccountRepository users, RecordStore store, StoredRecordRepository records) {
        List<UserAccount> students = STUDENT_USERNAMES.stream()
                .map(username -> users.findByUsername(username)
                        .orElseThrow(() -> new IllegalStateException(
                                "Testing formal-application fixture requires the seeded " + username + " account.")))
                .toList();
        UserAccount teacher = users.findByUsername("teacher01")
                .orElseThrow(() -> new IllegalStateException(
                        "Testing formal-application fixture requires the seeded teacher01 account."));
        List<String> studentIds = students.stream().map(student -> student.getId().toString()).toList();

        ObjectNode groupPayload = store.object();
        groupPayload.set("studentIds", store.value(studentIds));
        createIfAbsent(records, store, new FixtureRecord("project_group", teacher.getId().toString(),
                PROJECT_ID.toString(), "ALLOCATED", groupPayload));

        ObjectNode applicationPayload = store.object();
        applicationPayload.put("topic", "Synthetic community garden water study");
        applicationPayload.put("initialSituation", "A model garden needs a clearer plan for water use.");
        applicationPayload.put("objective", "Compare simple watering plans using synthetic measurements.");
        applicationPayload.put("cooperationPartner", "");
        applicationPayload.set("individualTopics", store.value(List.of(
                entry(students.get(0), "Soil moisture observations"),
                entry(students.get(1), "Watering interval comparison"),
                entry(students.get(2), "Measurement display prototype"))));
        applicationPayload.set("investigationConcerns", store.value(List.of(
                textEntry(students.get(0), "How does the model soil retain water?"),
                textEntry(students.get(1), "Which interval uses the least simulated water?"),
                textEntry(students.get(2), "How can the results be compared clearly?"))));
        applicationPayload.set("plannedResults", store.value(List.of(
                textEntry(students.get(0), "A table of synthetic soil readings."),
                textEntry(students.get(1), "A comparison using generated measurements."),
                textEntry(students.get(2), "A paper prototype for displaying the comparison."))));
        applicationPayload.set("milestones", store.value(List.of(
                new FixtureMilestone("Complete synthetic measurements", LocalDate.parse("2027-03-01")),
                new FixtureMilestone("Review the model results", LocalDate.parse("2027-04-01")))));
        createIfAbsent(records, store, new FixtureRecord("formal_application",
                students.get(0).getId().toString(), PROJECT_ID.toString(), "SUBMITTED", applicationPayload));
    }

    private void createIfAbsent(StoredRecordRepository records, RecordStore store, FixtureRecord fixture) {
        StoredRecord existing = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                fixture.type(), fixture.parentId()).orElse(null);
        if (existing == null) {
            store.create(fixture.type(), fixture.ownerId(), fixture.parentId(), fixture.status(), fixture.payload());
        } else if (!fixture.status().equals(existing.getStatus())) {
            throw new IllegalStateException("The existing testing fixture " + fixture.type()
                    + " is not in " + fixture.status() + " state.");
        }
    }

    private FixtureStudentTopic entry(UserAccount student, String topic) {
        return new FixtureStudentTopic(student.getId().toString(), topic);
    }

    private FixtureStudentText textEntry(UserAccount student, String text) {
        return new FixtureStudentText(student.getId().toString(), text);
    }

    private record FixtureStudentTopic(String studentId, String topic) {
    }

    private record FixtureStudentText(String studentId, String text) {
    }

    private record FixtureMilestone(String text, LocalDate date) {
    }

    private record FixtureRecord(String type, String ownerId, String parentId, String status, ObjectNode payload) {
    }
}
