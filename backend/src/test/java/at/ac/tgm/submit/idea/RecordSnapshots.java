package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.data.StoredRecord;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Test-only, detached multiset fingerprints; equality assumes SHA-256 collision resistance. */
final class RecordSnapshots {
    static final int BUFFER_BYTES = 4096;

    private RecordSnapshots() {
    }

    static List<String> capture(Iterable<StoredRecord> records) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK must provide SHA-256", exception);
        }
        byte[] buffer = new byte[BUFFER_BYTES];
        List<String> entries = new ArrayList<>();
        for (StoredRecord record : records) {
            field(digest, buffer, record.getId() == null ? null : record.getId().toString());
            field(digest, buffer, record.getType());
            field(digest, buffer, record.getOwnerId());
            field(digest, buffer, record.getCollaborator1Id());
            field(digest, buffer, record.getCollaborator2Id());
            field(digest, buffer, record.getCollaborator3Id());
            field(digest, buffer, record.getCollaborator4Id());
            field(digest, buffer, record.getParentId());
            field(digest, buffer, record.getStatus());
            field(digest, buffer, record.getIdeaSchoolYear());
            field(digest, buffer, record.getReferenceType());
            field(digest, buffer, record.getPayload());
            field(digest, buffer, record.getCreatedAt() == null ? null : record.getCreatedAt().toString());
            entries.add(HexFormat.of().formatHex(digest.digest()));
        }
        entries.sort(null);
        return List.copyOf(entries);
    }

    private static void field(MessageDigest digest, byte[] buffer, String value) {
        // Signed big-endian code-unit count: -1 marks null, 0 marks empty.
        int length = value == null ? -1 : value.length();
        for (int shift = 24; shift >= 0; shift -= 8) {
            digest.update((byte) (length >>> shift));
        }
        if (value == null) {
            return;
        }
        // Encode Java UTF-16 code units directly, including unpaired surrogates.
        // No encoder replacement, payload-sized byte array, or substring copies.
        int used = 0;
        for (int index = 0; index < length; index++) {
            char unit = value.charAt(index);
            buffer[used++] = (byte) (unit >>> 8);
            buffer[used++] = (byte) unit;
            if (used == buffer.length) {
                digest.update(buffer, 0, used);
                used = 0;
            }
        }
        digest.update(buffer, 0, used);
    }
}
