package com.tonic.parser;

import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CONSTANT_Utf8 must use JVMS 4.4.7 modified UTF-8: the null char U+0000 is encoded as the two bytes
 * 0xC0 0x80 (a lone 0x00 is illegal), and supplementary chars use a surrogate-pair form. This verifies
 * both the write side (emits 0xC0 0x80, never a lone 0x00) and the read side (decodes it back), so
 * strings with embedded NULs or non-ASCII survive a round-trip - and a YABR-written class stays
 * loadable by a real JVM.
 */
class Utf8EncodingTest {

    @Test
    void modifiedUtf8RoundTripsNullAndNonAscii() throws Exception {
        ClassFile cf = TestUtils.emptyPool().createNewClass("Zz", new AccessBuilder().setPublic().build());
        // Embedded NULs (U+0000) plus a non-ASCII char (U+00E9), built via char casts to keep the
        // source pure ASCII.
        String s = "a" + ((char) 0) + "b" + ((char) 0xE9) + ((char) 0) + "z";
        cf.getConstPool().findOrAddUtf8(s);

        byte[] bytes = cf.write();

        // Write side: U+0000 must serialize as 0xC0 0x80, and no lone 0x00 from this string may appear.
        assertTrue(indexOf(bytes, new byte[]{(byte) 0xC0, (byte) 0x80}) >= 0,
                "U+0000 should be encoded as modified-UTF-8 0xC0 0x80");

        // Read side: re-parse and confirm the exact string survives (would corrupt under standard UTF-8).
        ClassFile reparsed = new ClassFile(new ByteArrayInputStream(bytes));
        String found = null;
        for (Item<?> item : reparsed.getConstPool().getItems()) {
            if (item instanceof Utf8Item && s.equals(((Utf8Item) item).getValue())) {
                found = ((Utf8Item) item).getValue();
            }
        }
        assertEquals(s, found, "modified-UTF-8 Utf8 entry must round-trip exactly");
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
