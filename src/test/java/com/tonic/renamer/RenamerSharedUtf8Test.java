package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the class-rename Utf8 sharing bug. Constant pools deduplicate Utf8 entries, so a
 * {@code CONSTANT_Class} name and an equal {@code CONSTANT_String} literal can reference the same
 * {@code Utf8Item}. Renaming the class must repoint the class reference to a fresh Utf8 rather than
 * mutating the shared one in place, otherwise the string literal is silently corrupted.
 */
class RenamerSharedUtf8Test {

    @Test
    void renamingClassPreservesEqualStringConstant() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();

        // The class that will be renamed.
        pool.createNewClass("com/test/Es", access);

        // A user class that both references com/test/Es AND holds the equal string literal "com/test/Es".
        // findOrAddClass and findOrAddString both go through findOrAddUtf8, so they share one Utf8 entry.
        ClassFile user = pool.createNewClass("com/test/User", access);
        ConstPool cp = user.getConstPool();
        cp.findOrAddClass("com/test/Es");
        cp.findOrAddString("com/test/Es");

        Renamer renamer = new Renamer(pool);
        renamer.mapClass("com/test/Es", "com/test/Renamed");
        renamer.applyUnsafe();

        assertTrue(hasStringConstant(cp, "com/test/Es"),
                "string constant \"com/test/Es\" was corrupted by the class rename");
        assertFalse(hasStringConstant(cp, "com/test/Renamed"),
                "string constant must not have been rewritten to the new class name");
    }

    private static boolean hasStringConstant(ConstPool cp, String value) {
        for (Item<?> item : cp.getItems()) {
            if (item instanceof StringRefItem) {
                Item<?> utf8 = cp.getItem(((StringRefItem) item).getValue());
                if (utf8 instanceof Utf8Item && value.equals(((Utf8Item) utf8).getValue())) {
                    return true;
                }
            }
        }
        return false;
    }
}
