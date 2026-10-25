package ac.at.tgm.submit.formal;

import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FormalApprovalGate {
    private static final String APPLICATION = "formal_application";

    private FormalApprovalGate() {
    }

    public static void requireApproved(RecordStore store, UUID projectId) {
        boolean approved = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                APPLICATION, projectId.toString())
                .filter(application -> "APPROVED".equals(application.getStatus()))
                .isPresent();
        if (!approved) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Project work requires an approved formal application.");
        }
    }
}
