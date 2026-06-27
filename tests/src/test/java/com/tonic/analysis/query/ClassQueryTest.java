package com.tonic.analysis.query;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.attribute.RecordAttribute;
import com.tonic.analysis.query.ast.Query;
import com.tonic.analysis.query.exec.QueryBatchRunner;
import com.tonic.analysis.query.parser.QueryParser;
import com.tonic.analysis.query.planner.ProbePlan;
import com.tonic.analysis.query.planner.QueryPlanner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end coverage of the class-level query vocabulary added on top of the engine: {@code class.super},
 * {@code class.interfaces}, the synthetic {@code record} modifier, the transitive {@code isSubtypeOf} relation
 * (over the shared hierarchy plumbed through {@code QueryBatchRunner}), reaching {@code class.*} from a
 * {@code FIND methods} query (the owner), the {@code method} selector on classes, and positional
 * {@code param(n).type}. Culminates in the flagship "static 4-param method in an abstract subtype" query.
 */
class ClassQueryTest {

    private static final String SOURCE =
            "package t;\n" +
            "interface Marker {}\n" +
            "interface Sub extends Marker {}\n" +
            "class Base {}\n" +
            "class Middle extends Base {}\n" +
            "abstract class Leaf extends Middle implements Sub {\n" +
            "    static int compute(int a, int b, String c, Object d) { return a; }\n" +
            "    void other() {}\n" +
            "}\n" +
            "class Impl implements Marker {}\n" +
            "class Plain {}\n";

    private static ClassPool pool;

    @BeforeAll
    static void setup() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");
        Path dir = Files.createTempDirectory("query-class");
        Path src = dir.resolve("Fixture.java");
        Files.writeString(src, SOURCE);
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");
        pool = new ClassPool(true);
        try (Stream<Path> files = Files.list(dir.resolve("t"))) {
            for (Path p : files.filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList())) {
                pool.loadClass(Files.readAllBytes(p));
            }
        }
    }

    private List<String> classes(String query) throws Exception {
        Query parsed = new QueryParser().parse(query);
        ProbePlan plan = new QueryPlanner().plan(parsed);
        return new QueryBatchRunner(pool).run(plan, null).matches().stream()
                .map(r -> String.valueOf(r.getAttribute("class")))
                .collect(Collectors.toList());
    }

    private List<String> methods(String query) throws Exception {
        Query parsed = new QueryParser().parse(query);
        ProbePlan plan = new QueryPlanner().plan(parsed);
        return new QueryBatchRunner(pool).run(plan, null).matches().stream()
                .map(r -> r.getAttribute("class") + "." + r.getAttribute("method"))
                .collect(Collectors.toList());
    }

    @Test
    void directSuperclass() throws Exception {
        assertEquals(List.of("t/Leaf"), classes("FIND classes WHERE class.super == t.Middle"));
    }

    @Test
    void transitiveSubtypeOfClass() throws Exception {
        List<String> result = classes("FIND classes WHERE class isSubtypeOf t.Base");
        assertTrue(result.contains("t/Leaf"), "Leaf transitively extends Base");
        assertTrue(result.contains("t/Middle"), "Middle directly extends Base");
        assertFalse(result.contains("t/Base"), "a class is not its own subtype");
        assertFalse(result.contains("t/Plain"), "unrelated class");
    }

    @Test
    void transitiveSubtypeOfInterface() throws Exception {
        List<String> result = classes("FIND classes WHERE class isSubtypeOf t.Marker");
        assertTrue(result.contains("t/Impl"), "Impl implements Marker directly");
        assertTrue(result.contains("t/Leaf"), "Leaf implements Sub which extends Marker");
    }

    @Test
    void directInterfaces() throws Exception {
        // Marker is a direct superinterface of both Impl (implements) and Sub (interface extends).
        List<String> result = classes("FIND classes WHERE class.interfaces contains t.Marker");
        assertTrue(result.contains("t/Impl"));
        assertTrue(result.contains("t/Sub"));
        assertFalse(result.contains("t/Leaf"), "Leaf declares Sub, not Marker, as its interface");
    }

    @Test
    void recordModifier() throws Exception {
        // The build toolchain is JDK 11 (no records), so attach a RecordAttribute the way a real record carries
        // one - the synthetic `record` modifier is derived from its presence (mirrors ClassDecompiler).
        ClassFile plain = pool.getClasses().stream()
                .filter(c -> c.getClassName().equals("t/Plain")).findFirst().orElseThrow();
        plain.getClassAttributes().add(new RecordAttribute("Record", plain, 0, 0));
        try {
            assertEquals(List.of("t/Plain"), classes("FIND classes WHERE class.modifiers contains record"));
        } finally {
            plain.getClassAttributes().removeIf(a -> a instanceof RecordAttribute);
        }
    }

    @Test
    void abstractModifier() throws Exception {
        // Interfaces carry ACC_ABSTRACT too, so narrow to a genuinely abstract class.
        assertEquals(List.of("t/Leaf"), classes(
                "FIND classes WHERE class.modifiers contains abstract AND NOT class.modifiers contains interface"));
    }

    @Test
    void classAccessorsReachableFromMethodQuery() throws Exception {
        assertEquals(List.of("t/Leaf.compute"),
                methods("FIND methods WHERE method.name == \"compute\" AND class.super == t.Middle"));
    }

    @Test
    void positionalParamTypes() throws Exception {
        assertEquals(List.of("t/Leaf.compute"), methods(
                "FIND methods WHERE method.name == \"compute\" AND param(0).type == int "
                        + "AND param(2).type == java.lang.String AND param(3).type == java.lang.Object"));
    }

    @Test
    void classQuantifiesOverMethods() throws Exception {
        assertEquals(List.of("t/Leaf"),
                classes("FIND classes WHERE has method where (method.name == \"compute\")"));
    }

    @Test
    void flagshipAppletLikeQuery() throws Exception {
        assertEquals(List.of("t/Leaf.compute"), methods(
                "FIND methods WHERE method.modifiers contains static AND method.arity == 4 "
                        + "AND param(0).type == int AND param(1).type == int AND param(2).type == java.lang.String "
                        + "AND class.modifiers contains abstract AND class isSubtypeOf t.Base"));
    }
}
