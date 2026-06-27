package com.tonic.analysis.cpg.taint;

import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import lombok.Getter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class TaintQuery {

    private final CodePropertyGraph cpg;
    private final List<TaintSource> sources;
    private final List<TaintSink> sinks;
    private final Set<Sanitizer> sanitizers;
    private int maxPathLength;
    private boolean interprocedural;

    public TaintQuery(CodePropertyGraph cpg) {
        this.cpg = cpg;
        this.sources = new ArrayList<>();
        this.sinks = new ArrayList<>();
        this.sanitizers = new LinkedHashSet<>();
        this.maxPathLength = 50;
        this.interprocedural = true;
    }

    public TaintQuery addSource(TaintSource source) {
        sources.add(source);
        return this;
    }

    public TaintQuery addSources(TaintSource... sources) {
        Collections.addAll(this.sources, sources);
        return this;
    }

    public TaintQuery addSink(TaintSink sink) {
        sinks.add(sink);
        return this;
    }

    public TaintQuery addSinks(TaintSink... sinks) {
        Collections.addAll(this.sinks, sinks);
        return this;
    }

    public TaintQuery addSanitizer(Sanitizer sanitizer) {
        sanitizers.add(sanitizer);
        return this;
    }

    public TaintQuery addSanitizer(String ownerPattern, String methodPattern) {
        sanitizers.add(new Sanitizer(ownerPattern, methodPattern));
        return this;
    }

    public TaintQuery maxPathLength(int length) {
        this.maxPathLength = length;
        return this;
    }

    public TaintQuery interprocedural(boolean enabled) {
        this.interprocedural = enabled;
        return this;
    }

    public TaintQuery withDefaultSources() {
        addSource(TaintSource.httpParameter());
        addSource(TaintSource.httpHeader());
        addSource(TaintSource.httpCookie());
        addSource(TaintSource.consoleInput());
        addSource(TaintSource.fileRead());
        addSource(TaintSource.environmentVariable());
        addSource(TaintSource.databaseQuery());
        return this;
    }

    public TaintQuery withDefaultSinks() {
        addSink(TaintSink.sqlInjection());
        addSink(TaintSink.commandInjection());
        addSink(TaintSink.pathTraversal());
        addSink(TaintSink.xss());
        addSink(TaintSink.ldapInjection());
        addSink(TaintSink.xpathInjection());
        addSink(TaintSink.logInjection());
        addSink(TaintSink.ssrf());
        addSink(TaintSink.deserializationSink());
        addSink(TaintSink.reflectionSink());
        return this;
    }

    public TaintQuery withDefaultSanitizers() {
        addSanitizer("java/net/URLEncoder", "encode");
        addSanitizer("org/owasp/encoder/Encode", ".*");
        addSanitizer("org/apache/commons/text/StringEscapeUtils", "escape.*");
        addSanitizer("java/sql/PreparedStatement", "set.*");
        return this;
    }

    public List<CPGNode> findSourceNodes() {
        List<CPGNode> result = new ArrayList<>();

        for (CallSiteNode callSite : cpg.nodes(CallSiteNode.class).collect(Collectors.toList())) {
            for (TaintSource source : sources) {
                if (source.matches(callSite.getTargetOwner(),
                                   callSite.getTargetName(),
                                   callSite.getTargetDescriptor())) {
                    result.add(callSite);
                    break;
                }
            }
        }

        return result;
    }

    public List<CPGNode> findSinkNodes() {
        List<CPGNode> result = new ArrayList<>();

        for (CallSiteNode callSite : cpg.nodes(CallSiteNode.class).collect(Collectors.toList())) {
            for (TaintSink sink : sinks) {
                if (sink.matches(callSite.getTargetOwner(),
                                 callSite.getTargetName(),
                                 callSite.getTargetDescriptor())) {
                    result.add(callSite);
                    break;
                }
            }
        }

        return result;
    }

    public TaintAnalysisResult analyze() {
        List<CPGNode> sourceNodes = findSourceNodes();
        List<CPGNode> sinkNodes = findSinkNodes();

        TaintAnalysisResult result = new TaintAnalysisResult();

        for (CPGNode sourceNode : sourceNodes) {
            TaintSource matchedSource = findMatchingSource(sourceNode);
            if (matchedSource == null) continue;

            Set<CPGNode> reachableSinks = findReachableSinks(sourceNode, sinkNodes);

            for (CPGNode sinkNode : reachableSinks) {
                TaintSink matchedSink = findMatchingSink(sinkNode);
                if (matchedSink == null) continue;

                List<List<CPGNode>> paths = findPaths(sourceNode, sinkNode);

                for (List<CPGNode> path : paths) {
                    TaintPath taintPath = TaintPath.builder()
                        .source(matchedSource)
                        .sink(matchedSink)
                        .sourceNode(sourceNode)
                        .sinkNode(sinkNode)
                        .path(path)
                        .build();

                    checkSanitization(taintPath);
                    result.addPath(taintPath);
                }
            }
        }

        return result;
    }

    private TaintSource findMatchingSource(CPGNode node) {
        if (!(node instanceof CallSiteNode)) return null;
        CallSiteNode callSite = (CallSiteNode) node;

        for (TaintSource source : sources) {
            if (source.matches(callSite.getTargetOwner(),
                               callSite.getTargetName(),
                               callSite.getTargetDescriptor())) {
                return source;
            }
        }
        return null;
    }

    private TaintSink findMatchingSink(CPGNode node) {
        if (!(node instanceof CallSiteNode)) return null;
        CallSiteNode callSite = (CallSiteNode) node;

        for (TaintSink sink : sinks) {
            if (sink.matches(callSite.getTargetOwner(),
                             callSite.getTargetName(),
                             callSite.getTargetDescriptor())) {
                return sink;
            }
        }
        return null;
    }

    private Set<CPGNode> findReachableSinks(CPGNode source, List<CPGNode> sinks) {
        Set<CPGNode> reachable = new LinkedHashSet<>();
        Set<CPGNode> visited = new HashSet<>();
        Deque<CPGNode> worklist = new ArrayDeque<>();
        worklist.add(source);

        Set<CPGNode> sinkSet = new HashSet<>(sinks);

        while (!worklist.isEmpty()) {
            CPGNode current = worklist.poll();
            if (!visited.add(current)) continue;
            if (visited.size() > maxPathLength * 100) break;

            if (sinkSet.contains(current) && !current.equals(source)) {
                reachable.add(current);
            }

            for (CPGEdge edge : current.getOutgoingEdges()) {
                CPGEdgeType type = edge.getType();
                if (isDataFlowEdge(type) || (interprocedural && isCallEdge(type))) {
                    worklist.add(edge.getTarget());
                }
            }
        }

        return reachable;
    }

    private List<List<CPGNode>> findPaths(CPGNode source, CPGNode sink) {
        List<List<CPGNode>> paths = new ArrayList<>();
        Deque<List<CPGNode>> worklist = new ArrayDeque<>();

        List<CPGNode> initial = new ArrayList<>();
        initial.add(source);
        worklist.add(initial);

        while (!worklist.isEmpty()) {
            List<CPGNode> currentPath = worklist.poll();
            CPGNode current = currentPath.get(currentPath.size() - 1);

            if (currentPath.size() > maxPathLength) continue;

            if (current.equals(sink)) {
                paths.add(currentPath);
                if (paths.size() >= 10) break;
                continue;
            }

            for (CPGEdge edge : current.getOutgoingEdges()) {
                CPGEdgeType type = edge.getType();
                if (!isDataFlowEdge(type) && !(interprocedural && isCallEdge(type))) {
                    continue;
                }

                CPGNode next = edge.getTarget();
                if (currentPath.contains(next)) continue;

                List<CPGNode> newPath = new ArrayList<>(currentPath);
                newPath.add(next);
                worklist.add(newPath);
            }
        }

        return paths;
    }

    private boolean isDataFlowEdge(CPGEdgeType type) {
        return type == CPGEdgeType.DATA_DEF ||
               type == CPGEdgeType.DATA_USE ||
               type == CPGEdgeType.REACHING_DEF ||
               type == CPGEdgeType.TAINT;
    }

    private boolean isCallEdge(CPGEdgeType type) {
        return type == CPGEdgeType.CALL ||
               type == CPGEdgeType.PARAM_IN ||
               type == CPGEdgeType.PARAM_OUT ||
               type == CPGEdgeType.RETURN_VALUE;
    }

    private void checkSanitization(TaintPath path) {
        for (CPGNode node : path.getPath()) {
            if (!(node instanceof CallSiteNode)) continue;
            CallSiteNode callSite = (CallSiteNode) node;

            for (Sanitizer sanitizer : sanitizers) {
                if (sanitizer.matches(callSite.getTargetOwner(), callSite.getTargetName())) {
                    path.addSanitizer(callSite.getTargetOwner() + "." + callSite.getTargetName());
                }
            }
        }
    }

    @Getter
    public static class Sanitizer {
        private final String ownerPattern;
        private final String methodPattern;
        private final Pattern compiledOwner;
        private final Pattern compiledMethod;

        public Sanitizer(String ownerPattern, String methodPattern) {
            this.ownerPattern = ownerPattern;
            this.methodPattern = methodPattern;
            this.compiledOwner = Pattern.compile(ownerPattern);
            this.compiledMethod = Pattern.compile(methodPattern);
        }

        public boolean matches(String owner, String method) {
            return compiledOwner.matcher(owner).matches() &&
                   compiledMethod.matcher(method).matches();
        }
    }
}
