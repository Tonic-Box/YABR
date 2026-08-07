package com.tonic.analysis.cpg.taint;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collected taint paths from one analysis run, indexed by vulnerability type and severity.
 */
public class TaintAnalysisResult
{

    private final List<TaintPath> paths;
    private final Map<VulnerabilityType, List<TaintPath>> pathsByVulnerability;
    private final Map<Severity, List<TaintPath>> pathsBySeverity;

    /**
     * Creates an empty result.
     */
    public TaintAnalysisResult()
    {
        this.paths = new ArrayList<>();
        this.pathsByVulnerability = new EnumMap<>(VulnerabilityType.class);
        this.pathsBySeverity = new EnumMap<>(Severity.class);
    }

    /**
     * @return the paths
     */
    public List<TaintPath> getPaths()
    {
        return paths;
    }

    /**
     * @return the paths by vulnerability
     */
    public Map<VulnerabilityType, List<TaintPath>> getPathsByVulnerability()
    {
        return pathsByVulnerability;
    }

    /**
     * @return the paths by severity
     */
    public Map<Severity, List<TaintPath>> getPathsBySeverity()
    {
        return pathsBySeverity;
    }

    /**
     * Records a taint path under its vulnerability type and severity.
     * @param path the path to record
     */
    public void addPath(TaintPath path)
    {
        paths.add(path);
        pathsByVulnerability.computeIfAbsent(path.getVulnerabilityType(), k -> new ArrayList<>()).add(path);
        pathsBySeverity.computeIfAbsent(path.getSeverity(), k -> new ArrayList<>()).add(path);
    }

    /**
     * @return the total number of recorded paths, sanitized included
     */
    public int getTotalVulnerabilities()
    {
        return paths.size();
    }

    /**
     * @return the number of unsanitized paths
     */
    public int getUnsanitizedCount()
    {
        return (int) paths.stream().filter(p -> !p.isSanitized()).count();
    }

    /**
     * @return the number of sanitized paths
     */
    public int getSanitizedCount()
    {
        return (int) paths.stream().filter(TaintPath::isSanitized).count();
    }

    /**
     * @return the paths with no sanitizer on the way
     */
    public List<TaintPath> getUnsanitizedPaths()
    {
        return paths.stream().filter(p -> !p.isSanitized()).collect(Collectors.toList());
    }

    /**
     * @return the paths that passed through a sanitizer
     */
    public List<TaintPath> getSanitizedPaths()
    {
        return paths.stream().filter(TaintPath::isSanitized).collect(Collectors.toList());
    }

    /**
     * Looks up paths of one vulnerability type.
     * @param type the vulnerability type
     * @return the matching paths, or an empty list
     */
    public List<TaintPath> getPathsByVulnerability(VulnerabilityType type)
    {
        return pathsByVulnerability.getOrDefault(type, Collections.emptyList());
    }

    /**
     * Looks up paths of one severity.
     * @param severity the severity level
     * @return the matching paths, or an empty list
     */
    public List<TaintPath> getPathsBySeverity(Severity severity)
    {
        return pathsBySeverity.getOrDefault(severity, Collections.emptyList());
    }

    /**
     * @return the unsanitized paths of critical severity
     */
    public List<TaintPath> getCriticalPaths()
    {
        return getPathsBySeverity(Severity.CRITICAL).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
    }

    /**
     * @return the unsanitized paths of high severity
     */
    public List<TaintPath> getHighPaths()
    {
        return getPathsBySeverity(Severity.HIGH).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
    }

    /**
     * @return whether any unsanitized path was found
     */
    public boolean hasVulnerabilities()
    {
        return getUnsanitizedCount() > 0;
    }

    /**
     * @return whether any unsanitized critical path was found
     */
    public boolean hasCriticalVulnerabilities()
    {
        return !getCriticalPaths().isEmpty();
    }

    /**
     * Tallies unsanitized paths per vulnerability type.
     * @return a map from vulnerability type to path count
     */
    public Map<VulnerabilityType, Integer> getVulnerabilityCounts()
    {
        Map<VulnerabilityType, Integer> counts = new EnumMap<>(VulnerabilityType.class);
        for (TaintPath path : getUnsanitizedPaths())
        {
            counts.merge(path.getVulnerabilityType(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Tallies unsanitized paths per severity.
     * @return a map from severity to path count
     */
    public Map<Severity, Integer> getSeverityCounts()
    {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (TaintPath path : getUnsanitizedPaths())
        {
            counts.merge(path.getSeverity(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Renders totals plus per-severity and per-type breakdowns.
     * @return a multi-line summary
     */
    public String getSummary()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Taint Analysis Summary ===\n");
        sb.append(String.format("Total paths found: %d\n", getTotalVulnerabilities()));
        sb.append(String.format("Unsanitized: %d\n", getUnsanitizedCount()));
        sb.append(String.format("Sanitized: %d\n", getSanitizedCount()));
        sb.append("\n");

        Map<Severity, Integer> severityCounts = getSeverityCounts();
        if (!severityCounts.isEmpty())
        {
            sb.append("By Severity:\n");
            for (Severity sev : Severity.values())
            {
                int count = severityCounts.getOrDefault(sev, 0);
                if (count > 0)
                {
                    sb.append(String.format("  %s: %d\n", sev, count));
                }
            }
            sb.append("\n");
        }

        Map<VulnerabilityType, Integer> vulnCounts = getVulnerabilityCounts();
        if (!vulnCounts.isEmpty())
        {
            sb.append("By Vulnerability Type:\n");
            for (Map.Entry<VulnerabilityType, Integer> entry : vulnCounts.entrySet())
            {
                sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    /**
     * Renders the summary followed by each critical, high, and medium unsanitized path.
     * @return a multi-line report
     */
    public String getDetailedReport()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary());
        sb.append("\n");
        sb.append("=== Detailed Findings ===\n\n");

        List<TaintPath> critical = getCriticalPaths();
        if (!critical.isEmpty())
        {
            sb.append("--- CRITICAL ---\n");
            for (TaintPath path : critical)
            {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        List<TaintPath> high = getHighPaths();
        if (!high.isEmpty())
        {
            sb.append("--- HIGH ---\n");
            for (TaintPath path : high)
            {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        List<TaintPath> medium = getPathsBySeverity(Severity.MEDIUM).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
        if (!medium.isEmpty())
        {
            sb.append("--- MEDIUM ---\n");
            for (TaintPath path : medium)
            {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString()
    {
        return getSummary();
    }
}
