package com.tonic.analysis.cpg;

import com.tonic.analysis.callgraph.CallGraph;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.cpg.taint.*;
import com.tonic.analysis.graph.export.CPGDOTExporter;
import com.tonic.analysis.graph.export.DOTExporterConfig;
import com.tonic.analysis.graph.print.CPGPrinter;
import com.tonic.analysis.graph.print.GraphPrinterConfig;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.PDGBuilder;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.SDGBuilder;
import com.tonic.analysis.graph.export.PDGDOTExporter;
import com.tonic.analysis.graph.export.SDGDOTExporter;
import com.tonic.analysis.graph.print.PDGPrinter;
import com.tonic.analysis.graph.print.SDGPrinter;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import com.tonic.analysis.common.MethodReference;

import static org.junit.jupiter.api.Assertions.*;

class CPGIntegrationTest {

    private static final Path DEMO_JAR_PATH = Paths.get("C:/test/obber/DemoJar.jar");
    private static ClassPool pool;
    private static boolean jarAvailable = false;

    @BeforeAll
    static void setUp() {
        jarAvailable = Files.exists(DEMO_JAR_PATH);
        if (jarAvailable) {
            try {
                pool = new ClassPool(true);
                try (JarFile jar = new JarFile(DEMO_JAR_PATH.toFile())) {
                    pool.loadJar(jar);
                }
            } catch (IOException e) {
                jarAvailable = false;
            }
        }
    }

    static boolean isJarAvailable() {
        return jarAvailable;
    }

    @Test
    @EnabledIf("isJarAvailable")
    void buildCPGFromDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .withPDG()
            .build();

        assertNotNull(cpg);
        assertTrue(cpg.getNodeCount() > 0);
        assertTrue(cpg.getEdgeCount() > 0);
        assertTrue(cpg.getMethodCount() > 0);
    }

    @Test
    @EnabledIf("isJarAvailable")
    void queryMethodsInDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        List<CPGNode> methods = cpg.query().methods().toList();

        assertFalse(methods.isEmpty());
    }

    @Test
    @EnabledIf("isJarAvailable")
    void queryCallSitesInDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .build();

        List<CPGNode> callSites = cpg.query().callSites().toList();

        assertNotNull(callSites);
    }

    @Test
    @EnabledIf("isJarAvailable")
    void traverseCFGInDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        List<CPGNode> reachable = cpg.query()
            .instructions()
            .limit(1)
            .cfgReachable()
            .toList();

        assertNotNull(reachable);
    }

    @Test
    @EnabledIf("isJarAvailable")
    void buildPDGForDemoJarMethod() {
        for (ClassFile cf : pool.getClasses()) {
            String className = cf.getClassName();
            if (className.startsWith("java/")) continue;

            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                try {
                    SSA ssa = new SSA(cf.getConstPool());
                    IRMethod irMethod = ssa.lift(method);
                    if (irMethod == null) continue;

                    PDG pdg = PDGBuilder.build(irMethod);

                    assertNotNull(pdg);
                    assertTrue(pdg.hasEntryNode());
                    return;
                } catch (Exception e) {
                    // Continue to next method
                }
            }
        }
    }

    @Test
    @EnabledIf("isJarAvailable")
    void buildSDGForDemoJar() {
        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = buildIRMethods();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        assertNotNull(sdg);
    }

    private Map<MethodReference, IRMethod> buildIRMethods() {
        Map<MethodReference, IRMethod> irMethods = new HashMap<>();
        for (ClassFile cf : pool.getClasses()) {
            String className = cf.getClassName();
            if (className.startsWith("java/")) continue;
            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;
                try {
                    SSA ssa = new SSA(cf.getConstPool());
                    IRMethod irMethod = ssa.lift(method);
                    if (irMethod != null) {
                        MethodReference ref = new MethodReference(className, method.getName(), method.getDesc());
                        irMethods.put(ref, irMethod);
                    }
                } catch (Exception e) {
                    // Skip
                }
            }
        }
        return irMethods;
    }

    @Test
    @EnabledIf("isJarAvailable")
    void printCPGForDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        CPGPrinter printer = new CPGPrinter(GraphPrinterConfig.minimal());
        String output = printer.print(cpg);

        assertNotNull(output);
        assertFalse(output.isEmpty());
        assertTrue(output.contains("Code Property Graph"));
    }

    @Test
    @EnabledIf("isJarAvailable")
    void printPDGForDemoJarMethod() {
        for (ClassFile cf : pool.getClasses()) {
            String className = cf.getClassName();
            if (className.startsWith("java/")) continue;

            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                try {
                    SSA ssa = new SSA(cf.getConstPool());
                    IRMethod irMethod = ssa.lift(method);
                    if (irMethod == null) continue;

                    PDG pdg = PDGBuilder.build(irMethod);
                    PDGPrinter printer = new PDGPrinter(GraphPrinterConfig.minimal());
                    String output = printer.print(pdg);

                    assertNotNull(output);
                    assertTrue(output.contains("Program Dependence Graph"));
                    return;
                } catch (Exception e) {
                    // Continue to next method
                }
            }
        }
    }

    @Test
    @EnabledIf("isJarAvailable")
    void printSDGForDemoJar() {
        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = buildIRMethods();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        SDGPrinter printer = new SDGPrinter(GraphPrinterConfig.minimal());
        String output = printer.print(sdg);

        assertNotNull(output);
        assertTrue(output.contains("System Dependence Graph"));
    }

    @Test
    @EnabledIf("isJarAvailable")
    void exportCPGToDOT() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        CPGDOTExporter exporter = new CPGDOTExporter(DOTExporterConfig.compact());
        String dot = exporter.export(cpg);

        assertNotNull(dot);
        assertTrue(dot.contains("digraph"));
    }

    @Test
    @EnabledIf("isJarAvailable")
    void exportPDGToDOT() {
        for (ClassFile cf : pool.getClasses()) {
            String className = cf.getClassName();
            if (className.startsWith("java/")) continue;

            for (MethodEntry method : cf.getMethods()) {
                if (method.getCodeAttribute() == null) continue;

                try {
                    SSA ssa = new SSA(cf.getConstPool());
                    IRMethod irMethod = ssa.lift(method);
                    if (irMethod == null) continue;

                    PDG pdg = PDGBuilder.build(irMethod);
                    PDGDOTExporter exporter = new PDGDOTExporter(DOTExporterConfig.compact());
                    String dot = exporter.export(pdg);

                    assertNotNull(dot);
                    assertTrue(dot.contains("digraph"));
                    return;
                } catch (Exception e) {
                    // Continue
                }
            }
        }
    }

    @Test
    @EnabledIf("isJarAvailable")
    void exportSDGToDOT() {
        CallGraph callGraph = CallGraph.build(pool);
        Map<MethodReference, IRMethod> irMethods = buildIRMethods();
        SDG sdg = SDGBuilder.build(callGraph, irMethods);

        SDGDOTExporter exporter = new SDGDOTExporter(DOTExporterConfig.compact());
        String dot = exporter.export(sdg);

        assertNotNull(dot);
        assertTrue(dot.contains("digraph"));
    }

    @Test
    @EnabledIf("isJarAvailable")
    void taintAnalysisOnDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .withPDG()
            .build();

        TaintQuery query = new TaintQuery(cpg)
            .withDefaultSources()
            .withDefaultSinks()
            .maxPathLength(20);

        TaintAnalysisResult result = query.analyze();

        assertNotNull(result);
        assertNotNull(result.getSummary());
    }

    @Test
    @EnabledIf("isJarAvailable")
    void findMethodsByPatternInDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool).build();

        List<CPGNode> methods = cpg.query()
            .methods(".*main.*")
            .toList();

        assertNotNull(methods);
    }

    @Test
    @EnabledIf("isJarAvailable")
    void cpgStatisticsForDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .withPDG()
            .build();

        var edgeCounts = cpg.getEdgeTypeCounts();

        assertNotNull(edgeCounts);

        int cfgEdges = cpg.getEdgeCount(CPGEdgeType.CFG_NEXT) +
                      cpg.getEdgeCount(CPGEdgeType.CFG_TRUE) +
                      cpg.getEdgeCount(CPGEdgeType.CFG_FALSE);
        assertTrue(cfgEdges >= 0);
    }

    @Test
    @EnabledIf("isJarAvailable")
    void chainedQueryOnDemoJar() {
        CodePropertyGraph cpg = CPGBuilder.forClassPool(pool)
            .withCallGraph()
            .build();

        List<CPGNode> result = cpg.query()
            .methods()
            .out(CPGEdgeType.CONTAINS)
            .filterType(CPGNodeType.BLOCK)
            .filter(n -> n instanceof BlockNode && ((BlockNode) n).isEntryBlock())
            .limit(5)
            .toList();

        assertNotNull(result);
    }
}
