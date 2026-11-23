package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.ImportBatchId;
import java.time.Instant;
import java.util.List;

/**
 * What a test sees of an {@link ImportBatch}, read through its getters: a value that compares by
 * every part, which the entity doesn't. Tests compare a batch before and after a command, or with
 * the batch they expect, through it.
 *
 * @param id the batch
 * @param hash the uploaded file's hash
 * @param encoding how the file was decoded
 * @param rows its rows with their errors; empty once confirmed, discarded or expired
 * @param previewedAt when the preview was made
 * @param state previewed, confirmed, discarded or expired
 */
public record ImportBatchDescription(
        ImportBatchId id,
        FileHash hash,
        FileEncoding encoding,
        List<PreviewRow> rows,
        Instant previewedAt,
        ImportBatch.State state) {

    /**
     * Describes a batch.
     *
     * @param batch the batch
     * @return what its getters give
     */
    public static ImportBatchDescription of(ImportBatch batch) {
        return new ImportBatchDescription(
                batch.id(), batch.hash(), batch.encoding(), batch.rows(), batch.previewedAt(), batch.state());
    }
}
