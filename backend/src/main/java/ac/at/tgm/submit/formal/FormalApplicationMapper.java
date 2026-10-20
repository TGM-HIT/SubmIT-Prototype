package ac.at.tgm.submit.formal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.web.ApiDtos.FormalApplicationResponse;
import ac.at.tgm.submit.web.ApiDtos.FormalReviewResponse;
import ac.at.tgm.submit.web.ApiDtos.IndividualTopic;
import ac.at.tgm.submit.web.ApiDtos.Milestone;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class FormalApplicationMapper {
    private final RecordStore store;

    FormalApplicationMapper(RecordStore store) {
        this.store = store;
    }

    FormalApplicationResponse response(StoredRecord record, PageResponse<FormalReviewResponse> reviewHistory) {
        ObjectNode payload = store.parse(record);
        return new FormalApplicationResponse(record.getId(), UUID.fromString(record.getParentId()),
                record.getStatus(), payload.path("topic").asText(),
                individualTopics(payload.path("individualTopics")),
                payload.path("initialSituation").asText(),
                studentTexts(payload.path("investigationConcerns")),
                payload.path("objective").asText(), studentTexts(payload.path("plannedResults")),
                milestones(payload.path("milestones")), payload.path("cooperationPartner").asText(null),
                reviewHistory);
    }

    FormalReviewResponse reviewResponse(StoredRecord record) {
        ObjectNode payload = store.parse(record);
        return new FormalReviewResponse(record.getId(), UUID.fromString(record.getOwnerId()),
                record.getStatus(), payload.path("feedback").asText());
    }

    PageResponse<FormalReviewResponse> reviewPage(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::reviewResponse).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    private List<IndividualTopic> individualTopics(JsonNode entries) {
        List<IndividualTopic> values = new ArrayList<>();
        entries.forEach(entry -> values.add(new IndividualTopic(
                UUID.fromString(entry.path("studentId").asText()), entry.path("topic").asText())));
        return List.copyOf(values);
    }

    private List<StudentText> studentTexts(JsonNode entries) {
        List<StudentText> values = new ArrayList<>();
        entries.forEach(entry -> values.add(new StudentText(
                UUID.fromString(entry.path("studentId").asText()), entry.path("text").asText())));
        return List.copyOf(values);
    }

    private List<Milestone> milestones(JsonNode entries) {
        List<Milestone> values = new ArrayList<>();
        entries.forEach(entry -> values.add(new Milestone(entry.path("text").asText(),
                LocalDate.parse(entry.path("date").asText()))));
        return List.copyOf(values);
    }
}
