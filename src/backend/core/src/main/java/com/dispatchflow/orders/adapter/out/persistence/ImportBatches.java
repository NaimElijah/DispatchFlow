package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.ImportBatch;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaImportBatchRepository}. */
interface ImportBatches extends Repository<ImportBatch, UUID> {

    /**
     * Saves an import.
     *
     * @param batch the import
     * @return the managed import
     */
    ImportBatch save(ImportBatch batch);

    /**
     * Loads an import with its rows.
     *
     * @param id the import's UUID
     * @return the import, or empty
     */
    @EntityGraph(attributePaths = "rows")
    Optional<ImportBatch> findById(UUID id);

    /**
     * The open previews made before an instant.
     *
     * @param cutoff the instant a preview must predate
     * @return the imports' UUIDs, oldest preview first, and by key between previews made at once
     */
    @Query("""
            select b.id from ImportBatch b
            where b.state = PREVIEWED and b.previewedAt < :cutoff
            order by b.previewedAt, b.id
            """)
    List<UUID> previewsOlderThan(Instant cutoff);
}
