package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;

/**
 * Rule 16: a class that is no controller and maps no path, whose method takes a stream; it handles
 * no request, so the rule leaves it alone.
 */
public final class PlainClassTakesAStream {

    int firstByte(InputStream body) throws IOException {
        return body.read();
    }
}
