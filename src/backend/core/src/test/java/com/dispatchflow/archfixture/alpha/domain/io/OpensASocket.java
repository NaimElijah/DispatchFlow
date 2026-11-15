package com.dispatchflow.archfixture.alpha.domain.io;

import java.net.Socket;

/** Rule 4: java.net. */
public final class OpensASocket {

    static Socket socket() {
        return new Socket();
    }
}
