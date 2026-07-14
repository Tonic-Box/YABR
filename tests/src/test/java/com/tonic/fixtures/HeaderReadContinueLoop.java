package com.tonic.fixtures;

import java.io.IOException;
import java.io.Reader;

public class HeaderReadContinueLoop {
    Reader reader;
    StringBuilder buffer;

    /**
     * An infinite read loop whose header fuses a read-and-assign and whose conditional is an internal
     * continue (skip carriage returns), not the loop exit — the loop leaves only via the EOF return
     * deeper in the body. The header's stores must not be lifted with the skip test turned into a
     * break; doing so would exit on the first ordinary character.
     */
    void tokenize() throws IOException {
        while (true) {
            int ci = reader.read();
            char c = (char) ci;
            if (c == '\r') {
                continue;
            }
            if (ci == -1) {
                return;
            }
            buffer.append(c);
        }
    }
}
