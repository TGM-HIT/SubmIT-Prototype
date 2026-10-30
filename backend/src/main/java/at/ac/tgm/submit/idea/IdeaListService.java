package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IdeaListService {
    private static final String IDEA = "idea";
    private static final int IDEA_SCAN_BATCH_SIZE = 1000;
    private final RecordStore store;
    private final IdeaOwnership ownership;
    private final IdeaResponseMapper mapper;

    IdeaListService(RecordStore store, IdeaOwnership ownership, IdeaResponseMapper mapper) {
        this.store = store;
        this.ownership = ownership;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    ApiDtos.PageResponse<ApiDtos.IdeaResponse> list(AuthenticatedUser caller, IdeaListFilters filters, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        List<StoredRecord> ideas = new ArrayList<>();
        Page<StoredRecord> batch;
        int scanPage = 0;
        do {
            batch = store.page(IDEA, null, null, PageRequest.of(scanPage++, IDEA_SCAN_BATCH_SIZE));
            ideas.addAll(batch.getContent());
        } while (batch.hasNext());
        List<StoredRecord> visible = ideas.stream()
                .filter(idea -> filters.status() == null || filters.status().isBlank()
                        || filters.status().equals(idea.getStatus()))
                .filter(idea -> filters.schoolYear() == null
                        || filters.schoolYear().equals(store.parse(idea).path("schoolYear").asText()))
                .filter(idea -> filters.ownerId() == null || ownership.ownerIds(idea).contains(filters.ownerId()))
                .filter(idea -> !caller.hasRole(Role.STUDENT) || ownership.ownerIds(idea).contains(caller.id()))
                .toList();
        int start = Math.min((int) pageRequest.getOffset(), visible.size());
        int end = Math.min(start + pageRequest.getPageSize(), visible.size());
        Page<StoredRecord> result = new PageImpl<>(visible.subList(start, end), pageRequest, visible.size());
        return new ApiDtos.PageResponse<>(mapper.responses(result.getContent(), caller.role()), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
