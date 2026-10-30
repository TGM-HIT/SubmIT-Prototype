package at.ac.tgm.submit.formal;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.EligibleFormalProjectResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EligibleFormalProjectService {
    private static final String PROJECT_GROUP = "project_group";
    private static final String ALLOCATED = "ALLOCATED";
    private static final int MAX_PAGE_SIZE = 100;
    private final RecordStore store;
    private final EligibleFormalProjectMapper mapper;

    public EligibleFormalProjectService(RecordStore store, EligibleFormalProjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<EligibleFormalProjectResponse> list(AuthenticatedUser caller, int page, int size) {
        List<EligibleFormalProjectResponse> allProjects = eligibleProjects(caller.id().toString());
        allProjects.sort(Comparator.comparing(EligibleFormalProjectResponse::periodLabel,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(EligibleFormalProjectResponse::schoolYear)
                .thenComparing(project -> project.periodId().toString())
                .thenComparing(EligibleFormalProjectResponse::projectTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(project -> project.projectId().toString()));

        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int start = (int) Math.min((long) normalizedPage * normalizedSize, allProjects.size());
        int end = Math.min(start + normalizedSize, allProjects.size());
        int totalPages = (allProjects.size() + normalizedSize - 1) / normalizedSize;
        return new PageResponse<>(List.copyOf(allProjects.subList(start, end)), normalizedPage, normalizedSize,
                allProjects.size(), totalPages);
    }

    private List<EligibleFormalProjectResponse> eligibleProjects(String studentId) {
        List<EligibleFormalProjectResponse> projects = new ArrayList<>();
        Set<String> memberships = new HashSet<>();
        UUID lastGroupId = null;
        Page<StoredRecord> groupPage;
        do {
            PageRequest request = PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"));
            groupPage = lastGroupId == null
                    ? store.repository().findByTypeAndStatus(PROJECT_GROUP, ALLOCATED, request)
                    : store.repository().findByTypeAndStatusAndIdGreaterThan(PROJECT_GROUP, ALLOCATED,
                            lastGroupId, request);
            for (StoredRecord group : groupPage.getContent()) {
                lastGroupId = group.getId();
                EligibleFormalProjectResponse project = mapper.map(group, studentId);
                if (project != null && memberships.add(project.periodId() + ":" + project.projectId())) {
                    projects.add(project);
                }
            }
        } while (groupPage.hasNext());
        return projects;
    }
}
