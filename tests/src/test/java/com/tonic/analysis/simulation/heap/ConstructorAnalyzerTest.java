package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Reads a real compiled constructor and checks the fields it assigns, including that a supplied
 * argument reaches the field it is stored into.
 */
class ConstructorAnalyzerTest
{

    private static final String SOURCE =
            "public class Pt {\n"
            + "    int x;\n"
            + "    String name;\n"
            + "    Object untouched;\n"
            + "    Pt(int x, String name) { this.x = x; this.name = name; }\n"
            + "}\n";

    private static IRMethod constructor;

    @BeforeAll
    static void liftConstructor() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "no JDK compiler available");

        Path dir = Files.createTempDirectory("ctor-analyzer");
        Path src = dir.resolve("Pt.java");
        Files.write(src, SOURCE.getBytes());
        assumeTrue(compiler.run(null, null, null, "-g", "-d", dir.toString(), src.toString()) == 0, "fixture compiled");

        ClassFile cf = new ClassPool(true).loadClass(Files.readAllBytes(dir.resolve("Pt.class")));
        MethodEntry ctor = null;
        for (MethodEntry method : cf.getMethods())
        {
            if ("<init>".equals(method.getName()))
            {
                ctor = method;
            }
        }
        assertNotNull(ctor, "Pt must have a constructor");
        constructor = new SSA(cf.getConstPool()).lift(ctor);
    }

    private static List<SimValue> arguments()
    {
        List<SimValue> args = new ArrayList<>();
        args.add(SimValue.constant(42, PrimitiveType.INT, null));
        args.add(SimValue.constant("hello", IRType.fromDescriptor("Ljava/lang/String;"), null));
        return args;
    }

    @Test
    void recordsOnlyTheFieldsTheConstructorAssigns()
    {
        Map<FieldKey, SimValue> assigned = new ConstructorAnalyzer().extractFieldAssignments(constructor, arguments());

        assertEquals(2, assigned.size());
        assertTrue(assigned.containsKey(FieldKey.of("Pt", "x", "I")));
        assertTrue(assigned.containsKey(FieldKey.of("Pt", "name", "Ljava/lang/String;")));
        assertTrue(assigned.keySet().stream().noneMatch(k -> "untouched".equals(k.getName())));
    }

    @Test
    void bindsAnArgumentToTheFieldItIsStoredInto()
    {
        Map<FieldKey, SimValue> assigned = new ConstructorAnalyzer().extractFieldAssignments(constructor, arguments());

        assertEquals(42, assigned.get(FieldKey.of("Pt", "x", "I")).getConstantValue());
        assertEquals("hello", assigned.get(FieldKey.of("Pt", "name", "Ljava/lang/String;")).getConstantValue());
    }

    @Test
    void buildsAnObjectCarryingThoseFields()
    {
        AllocationSite site = AllocationSite.of("Pt", 0, "Test.make()V");
        SimObject obj = new ConstructorAnalyzer().analyzeConstructor(site, constructor, arguments());

        assertEquals(site, obj.getSite());
        assertTrue(obj.hasField(FieldKey.of("Pt", "x", "I")));
        assertTrue(obj.hasField(FieldKey.of("Pt", "name", "Ljava/lang/String;")));
    }

    @Test
    void returnsABareObjectForANullMethod()
    {
        AllocationSite site = AllocationSite.of("Pt", 0, "Test.make()V");
        SimObject obj = new ConstructorAnalyzer().analyzeConstructor(site, null, arguments());

        assertEquals(site, obj.getSite());
        assertTrue(obj.getFieldKeys().isEmpty());
    }
}
