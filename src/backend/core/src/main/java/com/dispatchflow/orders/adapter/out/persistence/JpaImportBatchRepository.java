package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.application.port.out.ImportBatchRepository;
import com.dispatchflow.orders.domain.ImportBatch;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the imports in {@code orders.import_batch}, keyed by their IDs, and a preview's rows in
 * {@code orders.import_row}. A confirmed, discarded or expired batch has emptied its rows, so its
 * save deletes their rows from the table (NFR-31).
 */
@Component
@Transactional
class JpaImportBatchRepository implements ImportBatchRepository {

    private final ImportBatches batches;
    private final VersionedSave versionedSave;
    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param batches the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     * @param entityManager the transaction-bound entity manager, which {@code add} flushes
     */
    JpaImportBatchRepository(ImportBatches batches, VersionedSave versionedSave, EntityManager entityManager) {
        this.batches = Objects.requireNonNull(batches, "batches");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    public Optional<ImportBatch> get(ImportBatchId id) {
        return batches.findById(id.value());
    }

    @Override
    public void add(ImportBatch batch) {
        batches.save(batch);
        entityManager.flush();
    }

    @Override
    public void save(ImportBatch batch) {
        versionedSave.save(batch, batches::save);
    }

    @Override
    public List<ImportBatchId> previewsOlderThan(Instant cutoff) {
        return batches.previewsOlderThan(cutoff).stream().map(ImportBatchId::of).toList();
    }
}
