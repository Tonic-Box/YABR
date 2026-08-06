package com.tonic.analysis.pdg.slice;

import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;

import java.util.*;
import java.util.function.Predicate;

/**
 * Intraprocedural slicer over a program dependence graph.
 */
public class PDGSlicer
{

    private final PDG pdg;
    private boolean includeControlDependencies = true;
    private boolean includeDataDependencies = true;

    /**
     * Creates a slicer following both control and data dependences.
     * @param pdg the graph to slice
     */
    public PDGSlicer(PDG pdg)
    {
        this.pdg = pdg;
    }

    /**
     * @return the pdg
     */
    public PDG getPdg()
    {
        return pdg;
    }

    /**
     * @return whether include control dependencies
     */
    public boolean isIncludeControlDependencies()
    {
        return includeControlDependencies;
    }

    /**
     * @return whether include data dependencies
     */
    public boolean isIncludeDataDependencies()
    {
        return includeDataDependencies;
    }

    /**
     * Sets whether control dependence edges are traversed.
     * @param include whether to follow control dependences
     * @return this slicer
     */
    public PDGSlicer withControlDependencies(boolean include)
    {
        this.includeControlDependencies = include;
        return this;
    }

    /**
     * Sets whether data dependence edges are traversed.
     * @param include whether to follow data dependences
     * @return this slicer
     */
    public PDGSlicer withDataDependencies(boolean include)
    {
        this.includeDataDependencies = include;
        return this;
    }

    /**
     * Slices backwards from a single node.
     * @param criterion the slicing criterion, may be null
     * @return the nodes and edges that can affect the criterion, empty if it is null
     */
    public SliceResult backwardSlice(PDGNode criterion)
    {
        if (criterion == null)
        {
            return new SliceResult(SliceResult.SliceType.BACKWARD, Collections.emptySet());
        }
        Set<PDGNode> criterionSet = new LinkedHashSet<>();
        criterionSet.add(criterion);
        return backwardSlice(criterionSet);
    }

    /**
     * Walks incoming edges transitively from every criterion node.
     * @param criterion the slicing criteria
     * @return the nodes and edges that can affect the criteria
     */
    public SliceResult backwardSlice(Set<PDGNode> criterion)
    {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, criterion);

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>(criterion);

        while (!worklist.isEmpty())
        {
            PDGNode current = worklist.poll();
            if (!visited.add(current))
            {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges())
            {
                if (shouldFollowEdge(edge))
                {
                    result.addEdge(edge);
                    PDGNode source = edge.getSource();
                    if (!visited.contains(source))
                    {
                        worklist.add(source);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Slices forwards from a single node.
     * @param criterion the slicing criterion
     * @return the nodes and edges the criterion can affect
     */
    public SliceResult forwardSlice(PDGNode criterion)
    {
        Set<PDGNode> criterionSet = new LinkedHashSet<>();
        criterionSet.add(criterion);
        return forwardSlice(criterionSet);
    }

    /**
     * Walks outgoing edges transitively from every criterion node.
     * @param criterion the slicing criteria
     * @return the nodes and edges the criteria can affect
     */
    public SliceResult forwardSlice(Set<PDGNode> criterion)
    {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, criterion);

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>(criterion);

        while (!worklist.isEmpty())
        {
            PDGNode current = worklist.poll();
            if (!visited.add(current))
            {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges())
            {
                if (shouldFollowEdge(edge))
                {
                    result.addEdge(edge);
                    PDGNode target = edge.getTarget();
                    if (!visited.contains(target))
                    {
                        worklist.add(target);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Intersects the forward slice of a source with the backward slice of a sink.
     * @param source the node to slice forward from
     * @param sink the node to slice backward from
     * @return the nodes on some dependence path from source to sink
     */
    public SliceResult chop(PDGNode source, PDGNode sink)
    {
        SliceResult forward = forwardSlice(source);
        SliceResult backward = backwardSlice(sink);
        return forward.intersect(backward);
    }

    /**
     * Intersects the forward slice of the sources with the backward slice of the sinks.
     * @param sources the nodes to slice forward from
     * @param sinks the nodes to slice backward from
     * @return the nodes on some dependence path from a source to a sink
     */
    public SliceResult chop(Set<PDGNode> sources, Set<PDGNode> sinks)
    {
        SliceResult forward = forwardSlice(sources);
        SliceResult backward = backwardSlice(sinks);
        return forward.intersect(backward);
    }

    /**
     * Slices backwards, stopping the walk at any node the filter rejects.
     * @param criterion the slicing criterion
     * @param filter predicate a node must satisfy to be kept and traversed through
     * @return the accepted nodes and the edges between them
     */
    public SliceResult backwardSliceWithFilter(PDGNode criterion, Predicate<PDGNode> filter)
    {
        SliceResult result = new SliceResult(SliceResult.SliceType.BACKWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty())
        {
            PDGNode current = worklist.poll();
            if (!visited.add(current))
            {
                continue;
            }

            if (!filter.test(current))
            {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getIncomingEdges())
            {
                if (shouldFollowEdge(edge))
                {
                    result.addEdge(edge);
                    PDGNode source = edge.getSource();
                    if (!visited.contains(source))
                    {
                        worklist.add(source);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Slices forwards, stopping the walk at any node the filter rejects.
     * @param criterion the slicing criterion
     * @param filter predicate a node must satisfy to be kept and traversed through
     * @return the accepted nodes and the edges between them
     */
    public SliceResult forwardSliceWithFilter(PDGNode criterion, Predicate<PDGNode> filter)
    {
        SliceResult result = new SliceResult(SliceResult.SliceType.FORWARD, Set.of(criterion));

        Set<PDGNode> visited = new LinkedHashSet<>();
        Deque<PDGNode> worklist = new ArrayDeque<>();
        worklist.add(criterion);

        while (!worklist.isEmpty())
        {
            PDGNode current = worklist.poll();
            if (!visited.add(current))
            {
                continue;
            }

            if (!filter.test(current))
            {
                continue;
            }

            result.addNode(current);

            for (PDGEdge edge : current.getOutgoingEdges())
            {
                if (shouldFollowEdge(edge))
                {
                    result.addEdge(edge);
                    PDGNode target = edge.getTarget();
                    if (!visited.contains(target))
                    {
                        worklist.add(target);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Slices backwards over control dependences only, leaving this slicer's settings untouched.
     * @param criterion the slicing criterion
     * @return the control-only backward slice
     */
    public SliceResult backwardSliceControlOnly(PDGNode criterion)
    {
        return new PDGSlicer(pdg)
            .withControlDependencies(true)
            .withDataDependencies(false)
            .backwardSlice(criterion);
    }

    /**
     * Slices backwards over data dependences only, leaving this slicer's settings untouched.
     * @param criterion the slicing criterion
     * @return the data-only backward slice
     */
    public SliceResult backwardSliceDataOnly(PDGNode criterion)
    {
        return new PDGSlicer(pdg)
            .withControlDependencies(false)
            .withDataDependencies(true)
            .backwardSlice(criterion);
    }

    /**
     * Slices forwards over control dependences only, leaving this slicer's settings untouched.
     * @param criterion the slicing criterion
     * @return the control-only forward slice
     */
    public SliceResult forwardSliceControlOnly(PDGNode criterion)
    {
        return new PDGSlicer(pdg)
            .withControlDependencies(true)
            .withDataDependencies(false)
            .forwardSlice(criterion);
    }

    /**
     * Slices forwards over data dependences only, leaving this slicer's settings untouched.
     * @param criterion the slicing criterion
     * @return the data-only forward slice
     */
    public SliceResult forwardSliceDataOnly(PDGNode criterion)
    {
        return new PDGSlicer(pdg)
            .withControlDependencies(false)
            .withDataDependencies(true)
            .forwardSlice(criterion);
    }

    /**
     * Enumerates the simple dependence paths from source to target by depth-first search.
     * @param source the start node
     * @param target the end node
     * @param maxDepth the maximum number of edges a path may use
     * @return every path found, each as a node list from source to target
     */
    public List<List<PDGNode>> findAllPaths(PDGNode source, PDGNode target, int maxDepth)
    {
        List<List<PDGNode>> allPaths = new ArrayList<>();
        List<PDGNode> currentPath = new ArrayList<>();
        Set<PDGNode> visited = new HashSet<>();

        findPathsDFS(source, target, currentPath, visited, allPaths, maxDepth);

        return allPaths;
    }

    private void findPathsDFS(PDGNode current, PDGNode target, List<PDGNode> currentPath, Set<PDGNode> visited, List<List<PDGNode>> allPaths, int remainingDepth)
    {
        if (remainingDepth < 0) return;

        currentPath.add(current);
        visited.add(current);

        if (current.equals(target))
        {
            allPaths.add(new ArrayList<>(currentPath));
        }
        else
        {
            for (PDGEdge edge : current.getOutgoingEdges())
            {
                if (shouldFollowEdge(edge))
                {
                    PDGNode next = edge.getTarget();
                    if (!visited.contains(next))
                    {
                        findPathsDFS(next, target, currentPath, visited, allPaths, remainingDepth - 1);
                    }
                }
            }
        }

        currentPath.remove(currentPath.size() - 1);
        visited.remove(current);
    }

    /**
     * Tests whether target appears in the forward slice of source.
     * @param source the start node
     * @param target the node to look for
     * @return whether a dependence path runs from source to target
     */
    public boolean isReachable(PDGNode source, PDGNode target)
    {
        SliceResult forward = forwardSlice(source);
        return forward.contains(target);
    }

    private boolean shouldFollowEdge(PDGEdge edge)
    {
        if (edge.isControlDependence() && !includeControlDependencies)
        {
            return false;
        }
        if (edge.isDataDependence() && !includeDataDependencies)
        {
            return false;
        }
        return !edge.isInterprocedural();
    }
}
