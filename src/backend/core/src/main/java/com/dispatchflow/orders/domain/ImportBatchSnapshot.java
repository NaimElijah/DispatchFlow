package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything an {@link ImportBatch} holds, as the persistence adapter stores and reloads it. {@link
 * ImportBatch#restore} checks the rules that hold at rest.
 *
 * @param id the batch
 * @param hash the uploaded file's hash
 * @param encoding how the file was decoded
 * @param rows its rows with their errors; empty once confirmed, discarded or expired
 * @param previewedAt when the preview was made, from which its hour counts
 * @param state previewed, confirmed, discarded or expired
 * @param version the version it was saved at
 */
public record ImportBatchSnapshot(
        ImportBatchId id,
        FileHash hash,
        FileEncoding encoding,
        List<PreviewRow> rows,
        Instant previewedAt,
        ImportBatch.State state,
        Version version) {

    /** Checks every part is given, and copies the rows. */
    public ImportBatchSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hash, "hash");
        Objects.requireNonNull(encoding, "encoding");
        rows = List.copyOf(rows);
        Objects.requireNonNull(previewedAt, "previewedAt");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(version, "version");
    }
}
