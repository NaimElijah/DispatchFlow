package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.CommonPasswords;

/**
 * Reads the bundled list of the 3,000 most common passwords of at least 8 characters, which {@code
 * NewPassword} refuses (NFR-95). The domain reads no file (DL §3.9).
 *
 * <p>To Be Implemented.
 */
public interface CommonPasswordList {

    /**
     * Reads the list.
     *
     * <p>To Be Implemented.
     *
     * @return the list
     */
    CommonPasswords load();
}
