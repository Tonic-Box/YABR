package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a value stored into a local slot AND used directly (as an if-condition) must share that
 * slot's variable. Here {@code boolean result = base(s); if (result) {...} return result;} - the
 * base() result is the condition and the returned slot's phi value. If the stored value split into a
 * separate synthetic, base()'s result would land in a temp while the returned variable stayed at its
 * default, so the method would always return false.
 */
public class ValueAsConditionReturnedTest {

    @Test
    public void storedValueUsedAsConditionSharesReturnedVariable() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("ValueAsConditionReturned");
        String src = ClassDecompiler.decompile(cf);
        String flat = src.replaceAll("\\s+", " ");

        // The variable initialized from base(...) must be the one returned - not a temp split off while a
        // defaulted variable is returned.
        Matcher m = Pattern.compile("(\\w+) = (?:this\\.)?base\\([^;]*\\);").matcher(flat);
        assertTrue(m.find(), "expected a variable initialized from base():\n" + src);
        String var = m.group(1);
        assertTrue(flat.contains("return " + var + ";"),
                "the base()-initialized variable must be the one returned, not split into a temp:\n" + src);
    }
}
