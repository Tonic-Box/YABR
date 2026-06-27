package com.tonic.analysis.pdg.slice;

import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import lombok.Getter;

import java.util.*;
import java.util.function.Predicate;

@Getter
public class PDGSlicer {

    private final PDG pdg;
    private boolean includeControlDependencies = true;
    private boolean includeDataDependencies = true;

    public PDGSlicer(PDG pdg) {
        this.pdg = pdg;
    }

    public PDGSlicer withControlDependencies(boolean include) {
        this.includeControlDependencies = include;
        return this;
    }

    public PDGSlicer withDataDependencies(boolean include) {
        this.includeDataDependencies = include;
        return this;
    }

    public SliceResult backwardSlice(PDGNode criterion) {
        if (criterion == null) {
            return new SliceResult(SliceResult.SliceType.BACKWARD, Collections.emptySet());
        }
        Set<PDGNode> criterionSet = new LinkedHashSet<>();
        criterionSet.add(criterion);
        return backwardSlice(criterionSet);
    }

    public SliceResult backwardSlice(Set<PDGNode> criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, criterion);

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                if (shouldFollowEdge(edge)) {
                    result.addEdge(edge);
                    PDGNode source = edge.getSource();
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                }
            }
        }

        return result;
    }

    public SliceResult forwardSlice(PDGNode criterion) {
        Set<PDGNode> criterionSet = new LinkedHashSet<>();
        criterionSet.add(criterion);
        return forwardSlice(criterionSet);
    }

    public SliceResult forwardSlice(Set<PDGNode> criterion) {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, criterion);

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges()) {
                if (shouldFollowEdge(edge)) {
                    result.addEdge(edge);
                    PDGNode target = edge.getTarget();
                    if (!visited.contains(target)) {
                        worklist.add(target);
                    }
                }
            }
        }

        return result;
    }

    public SliceResult chop(PDGNode source, PDGNode sink) {
        SliceResult forward = forwardSlice(source);
        SliceResult backward = backwardSlice(sink);
        return forward.intersect(backward);
    }

    public SliceResult chop(Set<PDGNode> sources, Set<PDGNode> sinks) {
        SliceResult forward = forwardSlice(sources);
        SliceResult backward = backwardSlice(sinks);
        return forward.intersect(backward);
    }

    public SliceResult backwardSliceWithFilter(PDGNode criterion, Predicate<PDGNode> filter) {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) {
                continue;
            }

            if (!filter.test(current)) {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges()) {
                if (shouldFollowEdge(edge)) {
                    result.addEdge(edge);
                    PDGNode source = edge.getSource();
                    if (!visited.contains(source)) {
                        worklist.add(source);
                    }
                }
            }
        }

        return result;
    }

    public SliceResult forwardSliceWithFilter(PDGNode criterion, Predicate<PDGNode> filter) {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty()) {
            PDGNode current = worklist.poll();
            if (!visited.add(current)) {
                continue;
            }

            if (!filter.test(current)) {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges()) {
                if (shouldFollowEdge(edge)) {
                    result.addEdge(edge);
                    PDGNode target = edge.getTarget();
                    if (!visited.contains(target)) {
                        worklist.add(target);
                    }
                }
            }
        }

        return result;
    }

    public SliceResult backwardSliceControlOnly(PDGNode criterion) {
        return new PDGSlicer(pdg)
            .withControlDependencies(true)
            .withDataDependencies(false)
            .backwardSlice(criterion);
    }

    public SliceResult backwardSliceDataOnly(PDGNode criterion) {
        return new PDGSlicer(pdg)
            .withControlDependencies(false)
            .withDataDependencies(true)
            .backwardSlice(criterion);
    }

    public SliceResult forwardSliceControlOnly(PDGNode criterion) {
        return new PDGSlicer(pdg)
            .withControlDependencies(true)
            .withDataDependencies(false)
            .forwardSlice(criterion);
    }

    public SliceResult forwardSliceDataOnly(PDGNode criterion) {
        return new PDGSlicer(pdg)
            .withControlDependencies(false)
            .withDataDependencies(true)
            .forwardSlice(criterion);
    }

    public List<List<PDGNode>> findAllPaths(PDGNode source, PDGNode target, int maxDepth) {
        List<List<PDGNode>> allPaths = new ArrayList<>();
        List<PDGNode> currentPath = new ArrayList<>();
        Set<PDGNode> visited = new HashSet<>();

        findPathsDFS(source, target, currentPath, visited, allPaths, maxDepth);

        return allPaths;
    }

    private void findPathsDFS(PDGNode current, PDGNode target,
                              List<PDGNode> currentPath, Set<PDGNode> visited,
                              List<List<PDGNode>> allPaths, int remainingDepth) {
        if (remainingDepth < 0) return;

        currentPath.add(current);
        visited.add(current);

        if (current.equals(target)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            for (PDGEdge edge : current.getOutgoingEdges()) {
                if (shouldFollowEdge(edge)) {
                    PDGNode next = edge.getTarget();
                    if (!visited.contains(next)) {
                        findPathsDFS(next, target, currentPath, visited, allPaths, remainingDepth - 1);
                    }
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    public boolean isReachable(PDGNode source, PDGNode target) {
        SliceResult forward = forwardSlice(source);
        return forward.contains(target);
    }

    private boolean shouldFollowEdge(PDGEdge edge) {
        if (edge.isControlDependence() && !includeControlDependencies) {
            return false;
        }
        if (edge.isDataDependence() && !includeDataDependencies) {
            return false;
        }
        return !edge.isInterprocedural();
    }
}
