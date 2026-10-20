package ac.at.tgm.submit.allocation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AllocationDeadlineScheduler {
    private final AllocationService allocationService;

    public AllocationDeadlineScheduler(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @Scheduled(fixedDelay = 30000)
    public void startDueAllocations() {
        allocationService.startDuePeriods();
    }
}
