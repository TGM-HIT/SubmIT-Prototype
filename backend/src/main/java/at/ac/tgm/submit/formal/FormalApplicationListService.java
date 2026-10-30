package at.ac.tgm.submit.formal;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.FormalApplicationSummaryResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormalApplicationListService {
    private static final String APPLICATION = "formal_application";
    private static final int SCAN_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 100;

    private final RecordStore store;
    private final FormalApplicationSummaryMapper mapper;

    public FormalApplicationListService(RecordStore store, FormalApplicationSummaryMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('TEACHER')")
    public PageResponse<FormalApplicationSummaryResponse> list(AuthenticatedUser caller, String query,
            boolean supervisedOnly, int page, int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<FormalApplicationSummaryResponse> results = new ArrayList<>();
        Page<StoredRecord> batch;
        int scanPage = 0;
        do {
            batch = store.page(APPLICATION, null, null,
                    PageRequest.of(scanPage++, SCAN_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            for (StoredRecord application : batch.getContent()) {
                FormalApplicationSummaryResponse summary = mapper.summary(application);
                if ((!supervisedOnly || mapper.isConfirmedSupervisor(application, caller.id().toString()))
                        && matches(summary, normalizedQuery)) {
                    results.add(summary);
                }
            }
        } while (batch.hasNext());

        results.sort(Comparator.comparing(FormalApplicationSummaryResponse::projectTitle,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(FormalApplicationSummaryResponse::projectTitle)
                .thenComparing(FormalApplicationSummaryResponse::periodLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(FormalApplicationSummaryResponse::schoolYear)
                .thenComparing(summary -> summary.projectId().toString())
                .thenComparing(summary -> summary.formalApplicationId().toString()));

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int start = (int) Math.min((long) normalizedPage * normalizedSize, results.size());
        int end = Math.min(start + normalizedSize, results.size());
        int totalPages = (results.size() + normalizedSize - 1) / normalizedSize;
        return new PageResponse<>(List.copyOf(results.subList(start, end)), normalizedPage, normalizedSize,
                results.size(), totalPages);
    }

    private boolean matches(FormalApplicationSummaryResponse summary, String query) {
        if (query.isEmpty()) {
            return true;
        }
        return contains(summary.projectTitle(), query)
                || contains(summary.periodLabel(), query)
                || contains(summary.schoolYear(), query)
                || summary.studentNames().stream().anyMatch(name -> contains(name, query));
    }

    private boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }
}
