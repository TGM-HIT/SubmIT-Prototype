package com.flightlog.allocation;

import com.flightlog.allocation.AllocationOptimizer.Application;
import com.flightlog.allocation.AllocationOptimizer.Project;
import com.flightlog.allocation.AllocationOptimizer.Request;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class AllocationEngine {
    private final RecordStore store;
    private final AllocationOptimizer optimizer;

    AllocationEngine(RecordStore store) {
        this.store = store;
        this.optimizer = new AllocationOptimizer();
    }

    Object optimize(List<String> projectIds, List<StoredRecord> records) {
        List<Project> projects = projectIds.stream().map(Project::new).toList();
        List<Application> applications = new ArrayList<>();
        for (StoredRecord record : records) {
            List<String> rankedIds = new ArrayList<>();
            store.parse(record).path("preferences").forEach(item ->
                    rankedIds.add(item.path("projectId").asText()));
            applications.add(new Application(record.getOwnerId(), rankedIds));
        }
        return optimizer.optimize(new Request(projects, applications));
    }
}
