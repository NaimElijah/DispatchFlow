package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.ImportBatch;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where imports are kept, as whole {@link ImportBatch} aggregates (DL §3.6, §4.3, ADR-086). A
 * confirmed, discarded or expired batch keeps none of its rows (NFR-31), so its save deletes them.
 *
 * <p>Built in M2.
 */
public interface ImportBatchRepository {

    /**
     * Loads an import, whatever its state.
     *
     * <p>Built in M2.
     *
     * @param id the import's ID
     * @return the import at its stored version, or empty when none has the ID
     */
    Optional<ImportBatch> get(ImportBatchId id);

    /**
     * Stores a new preview with its rows, in the caller's transaction.
     *
     * <p>Built in M2.
     *
     * @param batch the import, previewed, never saved
     */
    void add(ImportBatch batch);

    /**
     * Stores a changed import, in the caller's transaction, if its stored version is still the one
     * it was loaded at (BR-76, ADR-057).
     *
     * <p>Built in M2.
     *
     * @param batch the import
     * @throws StaleVersion when another change was saved since it was loaded
     */
    void save(ImportBatch batch);

    /**
     * The previews still open that were made before an instant, which the hourly sweep expires
     * (FR-399, ADR-086, DL §10.3).
     *
     * <p>Built in M2.
     *
     * @param cutoff the instant a preview must predate: the sweep's time less a preview's hour
     * @return the previews, each once, oldest first
     */
    List<ImportBatchId> previewsOlderThan(Instant cutoff);
}
