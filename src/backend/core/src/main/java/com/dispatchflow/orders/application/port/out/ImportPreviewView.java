package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.FileEncoding;
import com.dispatchflow.orders.domain.PreviewRow;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An import preview as the dispatcher checks it before confirming (FR-203, FR-204, FR-313, FR-399,
 * ADR-086): every row with every error found, the encoding the file was read in, and when the
 * preview expires. The rows are personal data, so it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param batch the import
 * @param encoding how the file was decoded; Windows-1255 needs the dispatcher's confirmation
 *     (FR-313)
 * @param expiresAt when the preview stops being confirmable, an hour after it was made (FR-399)
 * @param rejectedRows how many rows have at least one error, 0 or more; any refuses the
 *     confirmation (FR-204)
 * @param rows every row in row order, each with its errors (FR-203)
 * @param version the version the import was read at, for {@code If-Match} (BR-76)
 */
public record ImportPreviewView(
        ImportBatchId batch,
        FileEncoding encoding,
        Instant expiresAt,
        int rejectedRows,
        List<PreviewRow> rows,
        Version version)
        implements PersonalData {

    /** Checks every part is present, and copies the rows. */
    public ImportPreviewView {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(version, "version");
        rows = List.copyOf(rows);
    }

    /**
     * A form that hides the rows.
     *
     * @return {@code ImportPreviewView[redacted]}
     */
    @Override
    public String toString() {
        return "ImportPreviewView[redacted]";
    }
}
