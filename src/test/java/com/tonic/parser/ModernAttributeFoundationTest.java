package com.tonic.parser;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.PermittedSubclassesAttribute;
import com.tonic.parser.attribute.RecordAttribute;
import com.tonic.testutil.ModernJdk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Foundation: the Record (Java 16) and PermittedSubclasses (Java 17) attributes are modeled (not
 * opaque), expose their structured data, and survive a read -> (constant-pool growth) -> write ->
 * re-read round-trip with correct references. Fixtures are compiled with a real JDK 17 javac.
 */
public class ModernAttributeFoundationTest {

    private static final String SHAPE =
        "public sealed interface Shape permits Circle, Square { double area(); }";
    private static final String CIRCLE =
        "public record Circle(double radius) implements Shape { public double area() { return 3.14 * radius * radius; } }";
    private static final String SQUARE =
        "public record Square(double side) implements Shape { public double area() { return side * side; } }";

    private Map<String, ClassFile> compileShapes() throws Exception {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("Shape", SHAPE);
        src.put("Circle", CIRCLE);
        src.put("Square", SQUARE);
        Map<String, byte[]> compiled = ModernJdk.compile(17, src);
        Map<String, ClassFile> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : compiled.entrySet()) {
            out.put(e.getKey(), new ClassFile(new ByteArrayInputStream(e.getValue())));
        }
        return out;
    }

    private static RecordAttribute recordAttr(ClassFile cf) {
        return cf.getClassAttributes().stream()
                .filter(a -> a instanceof RecordAttribute).map(a -> (RecordAttribute) a)
                .findFirst().orElse(null);
    }

    private static PermittedSubclassesAttribute permitsAttr(ClassFile cf) {
        return cf.getClassAttributes().stream()
                .filter(a -> a instanceof PermittedSubclassesAttribute).map(a -> (PermittedSubclassesAttribute) a)
                .findFirst().orElse(null);
    }

    @Test
    public void recordAndPermittedSubclassesAreModeled() throws Exception {
        assumeTrue(ModernJdk.available(17), "JDK 17 not installed");
        Map<String, ClassFile> classes = compileShapes();

        RecordAttribute rec = recordAttr(classes.get("Circle"));
        assertNotNull(rec, "Circle must carry a modeled Record attribute");
        assertEquals(1, rec.getComponentNameAndDescriptors().size());
        assertArrayEquals(new String[]{"radius", "D"}, rec.getComponentNameAndDescriptors().get(0));

        PermittedSubclassesAttribute permits = permitsAttr(classes.get("Shape"));
        assertNotNull(permits, "sealed Shape must carry a modeled PermittedSubclasses attribute");
        assertEquals(java.util.List.of("Circle", "Square"), permits.getPermittedClassNames());
    }

    @Test
    public void byteStableRoundTrip() throws Exception {
        assumeTrue(ModernJdk.available(17), "JDK 17 not installed");
        Map<String, ClassFile> classes = compileShapes();
        for (String name : new String[]{"Shape", "Circle", "Square"}) {
            ClassFile cf = classes.get(name);
            byte[] out1 = cf.write();
            byte[] out2 = new ClassFile(new ByteArrayInputStream(out1)).write();
            assertArrayEquals(out1, out2, name + " must be byte-stable across re-read");
        }
    }

    @Test
    public void attributesSurviveConstantPoolGrowth() throws Exception {
        assumeTrue(ModernJdk.available(17), "JDK 17 not installed");
        Map<String, ClassFile> classes = compileShapes();

        ClassFile circle = classes.get("Circle");
        // Grow the constant pool (append-only); modeled-attribute indices must stay valid.
        circle.getConstPool().findOrAddUtf8("yabr$injected$marker");
        ClassFile reread = new ClassFile(new ByteArrayInputStream(circle.write()));
        RecordAttribute rec = recordAttr(reread);
        assertNotNull(rec);
        assertArrayEquals(new String[]{"radius", "D"}, rec.getComponentNameAndDescriptors().get(0),
                "Record component must still resolve after constant-pool growth");

        ClassFile shape = classes.get("Shape");
        shape.getConstPool().findOrAddUtf8("yabr$injected$marker");
        PermittedSubclassesAttribute permits = permitsAttr(new ClassFile(new ByteArrayInputStream(shape.write())));
        assertNotNull(permits);
        assertEquals(java.util.List.of("Circle", "Square"), permits.getPermittedClassNames(),
                "permits clause must still resolve after constant-pool growth");
    }

    @Test
    public void decompileDoesNotCrashOnModernClasses() throws Exception {
        assumeTrue(ModernJdk.available(17), "JDK 17 not installed");
        Map<String, ClassFile> classes = compileShapes();
        // Records/sealed are not reconstructed yet (later slices); decompilation must not crash.
        assertTrue(ClassDecompiler.decompile(classes.get("Circle")).length() > 0);
        assertTrue(ClassDecompiler.decompile(classes.get("Shape")).length() > 0);
    }
}
