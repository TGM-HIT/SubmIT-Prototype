package at.ac.tgm.submit.grading;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** A resolved immutable rubric version. */
record RubricRef(UUID id, ObjectNode payload) {
}
