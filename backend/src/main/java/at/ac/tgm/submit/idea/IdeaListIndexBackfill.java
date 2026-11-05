package at.ac.tgm.submit.idea;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class IdeaListIndexBackfill implements ApplicationRunner {
    private static final String IDEA = "idea";
    private static final int BATCH_SIZE = 500;
    private final StoredRecordRepository records;
    private final RecordStore store;
    private final EntityManager entityManager;

    IdeaListIndexBackfill(StoredRecordRepository records, RecordStore store, EntityManager entityManager) {
        this.records = records;
        this.store = store;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        int pageNumber = 0;
        Page<StoredRecord> page;
        do {
            page = records.findByTypeOrderByIdAsc(IDEA, PageRequest.of(pageNumber++, BATCH_SIZE));
            for (StoredRecord idea : page.getContent()) {
                store.synchronizeIdeaListIndex(idea, store.parse(idea));
            }
            records.flush();
            entityManager.clear();
        } while (page.hasNext());
    }
}
