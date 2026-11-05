package at.ac.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.tgm.submit.data.StoredRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordSnapshotsTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private StoredRecord record(String type, String payload) {
        return new StoredRecord(ID, new StoredRecord.Details(type, "owner", "parent", "status",
                "reference", payload, CREATED));
    }

    private List<String> snapshot(StoredRecord... records) {
        return RecordSnapshots.capture(List.of(records));
    }

    @Test
    void recordSnapshotsMatchIndependentOfOrderAndRemainDetached() {
        StoredRecord idea = record("idea", "original");
        StoredRecord audit = record("audit", "event");
        List<String> baseline = snapshot(idea, audit);
        assertEquals(baseline, snapshot(audit, idea));
        assertEquals(baseline, snapshot(idea, audit));
        assertEquals(List.of(), snapshot());
        assertThrows(UnsupportedOperationException.class, () -> baseline.add("entry"));
        List<String> saved = List.copyOf(baseline);
        idea.setPayload("modified");
        idea.setStatus("changed");
        idea.setOwnerId("another");
        assertEquals(saved, baseline);
        assertNotEquals(baseline, snapshot(idea, audit));
        idea.setPayload("original");
        idea.setStatus("status");
        idea.setOwnerId("owner");
        assertEquals(baseline, snapshot(idea, audit));
    }

    @Test
    void recordSnapshotsDetectEveryStoredFieldMutation() {
        for (String type : List.of("idea", "specification_revision", "audit", "unrelated")) {
            StoredRecord original = record(type, "payload");
            List<String> baseline = snapshot(original, record("constant", "unchanged"));
            for (int field = 0; field < 13; field++) {
                StoredRecord changed = new StoredRecord(field == 0 ? new UUID(0, 2) : ID,
                        new StoredRecord.Details(field == 1 ? type + "changed" : type,
                                field == 2 ? "other" : "owner", field == 7 ? "other" : "parent",
                                field == 8 ? "other" : "status", field == 10 ? "other" : "reference",
                                field == 11 ? "changed" : "payload",
                                field == 12 ? CREATED.plusNanos(1) : CREATED));
                changed.setIdeaListIndex(field == 3 ? "collaborator-1" : null,
                        field == 4 ? "collaborator-2" : null,
                        field == 5 ? "collaborator-3" : null,
                        field == 6 ? "collaborator-4" : null,
                        field == 9 ? "another-year" : null);
                assertNotEquals(baseline, snapshot(changed, record("constant", "unchanged")),
                        type + " field " + field);
            }
        }
    }

    @Test
    void recordSnapshotsDetectPayloadOnlyChangesAcrossChunks() {
        String payload = "a".repeat(RecordSnapshots.BUFFER_BYTES * 3);
        List<String> baseline = snapshot(record("idea", payload));
        assertEquals(baseline, snapshot(record("idea", new String(payload.toCharArray()))));
        for (int position : List.of(0, RecordSnapshots.BUFFER_BYTES / 2 - 1,
                RecordSnapshots.BUFFER_BYTES / 2, payload.length() - 1)) {
            char[] changed = payload.toCharArray();
            changed[position] = 'b';
            assertNotEquals(baseline, snapshot(record("idea", new String(changed))));
        }
    }

    @Test
    void recordSnapshotsDistinguishFieldBoundariesAndNulls() {
        List<String> values = java.util.Arrays.asList(null, "null", "", "\u001f", "a\u001fb", "a",
                "é中", "\ud800", "\ud801", "\ufffd",
                "x".repeat(RecordSnapshots.BUFFER_BYTES / 2 - 1) + "\ud83d\ude00tail");
        List<List<String>> seen = new ArrayList<>();
        for (String value : values) {
            List<String> captured = snapshot(record("idea", value));
            assertEquals(captured, snapshot(record("idea",
                    value == null ? null : new String(value.toCharArray()))));
            assertTrue(seen.stream().noneMatch(captured::equals));
            seen.add(captured);
        }
        String boundaryPair = "x".repeat(RecordSnapshots.BUFFER_BYTES / 2 - 1) + "\ud83d\ude00tail";
        assertNotEquals(snapshot(record("idea", boundaryPair)),
                snapshot(record("idea", boundaryPair.replace('\ude00', '\ude01'))));
        StoredRecord left = new StoredRecord(ID, new StoredRecord.Details("a\u001fb", "c",
                null, "", null, "payload", CREATED));
        StoredRecord right = new StoredRecord(ID, new StoredRecord.Details("a", "b\u001fc",
                null, "", null, "payload", CREATED));
        assertNotEquals(snapshot(left), snapshot(right));
        assertEquals(snapshot(left), snapshot(left));
        for (String value : java.util.Arrays.asList(null, "null", "")) {
            StoredRecord owner = new StoredRecord(ID, new StoredRecord.Details("idea", value,
                    "parent", "status", "reference", "payload", CREATED));
            for (String other : java.util.Arrays.asList(null, "null", "")) {
                if (!java.util.Objects.equals(value, other)) {
                    StoredRecord changed = new StoredRecord(ID, new StoredRecord.Details("idea", other,
                            "parent", "status", "reference", "payload", CREATED));
                    assertNotEquals(snapshot(owner), snapshot(changed));
                }
            }
        }
        StoredRecord nulls = new StoredRecord(null,
                new StoredRecord.Details(null, null, null, null, null, null, null));
        assertEquals(snapshot(nulls), snapshot(nulls));
        assertNotEquals(snapshot(nulls), snapshot(record("null", "null")));
    }

    @Test
    void recordSnapshotsDetectInsertionsDeletionsAndMultiplicity() {
        StoredRecord idea = record("idea", "payload");
        StoredRecord audit = record("audit", "event");
        List<String> baseline = snapshot(idea, audit);
        assertNotEquals(baseline, snapshot(idea));
        assertNotEquals(baseline, snapshot(idea, audit, idea));
        assertNotEquals(snapshot(idea), snapshot(new StoredRecord(new UUID(0, 2),
                new StoredRecord.Details("idea", "owner", "parent", "status", "reference", "payload", CREATED))));
        assertEquals(snapshot(idea, audit, idea), snapshot(idea, idea, audit));
        assertEquals(2, Collections.frequency(snapshot(idea, audit, idea), snapshot(idea).getFirst()));
    }

    @Test
    void recordSnapshotsKeepLargePayloadStateCompact() {
        StoredRecord small = record("idea", "small");
        StoredRecord large = record("idea", "A".repeat(4 * ((10 * 1024 * 1024 + 2) / 3)));
        List<String> smallState = snapshot(small);
        List<String> largeState = snapshot(large);
        assertEquals(smallState.size(), largeState.size());
        assertEquals(64, smallState.getFirst().length());
        assertEquals(64, largeState.getFirst().length());
        assertTrue(largeState.getFirst().matches("[0-9a-f]{64}"));
        assertEquals(largeState, snapshot(large));
        large.setPayload("changed");
        assertNotEquals(largeState, snapshot(large));
        assertEquals(64, largeState.getFirst().length());
        assertThrows(UnsupportedOperationException.class, () -> largeState.set(0, "changed"));
    }
}
