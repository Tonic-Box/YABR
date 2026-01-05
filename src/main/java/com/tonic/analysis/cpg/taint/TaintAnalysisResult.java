package com.tonic.analysis.cpg.taint;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

@Getter
public class TaintAnalysisResult {

    private final List<TaintPath> paths;
    private final Map<VulnerabilityType, List<TaintPath>> pathsByVulnerability;
    private final Map<Severity, List<TaintPath>> pathsBySeverity;

    public TaintAnalysisResult() {
        this.paths = new ArrayList<>();
        this.pathsByVulnerability = new EnumMap<>(VulnerabilityType.class);
        this.pathsBySeverity = new EnumMap<>(Severity.class);
    }

    public void addPath(TaintPath path) {
        paths.add(path);
        pathsByVulnerability.computeIfAbsent(path.getVulnerabilityType(), k -> new ArrayList<>()).add(path);
        pathsBySeverity.computeIfAbsent(path.getSeverity(), k -> new ArrayList<>()).add(path);
    }

    public int getTotalVulnerabilities() {
        return paths.size();
    }

    public int getUnsanitizedCount() {
        return (int) paths.stream().filter(p -> !p.isSanitized()).count();
    }

    public int getSanitizedCount() {
        return (int) paths.stream().filter(TaintPath::isSanitized).count();
    }

    public List<TaintPath> getUnsanitizedPaths() {
        return paths.stream().filter(p -> !p.isSanitized()).collect(Collectors.toList());
    }

    public List<TaintPath> getSanitizedPaths() {
        return paths.stream().filter(TaintPath::isSanitized).collect(Collectors.toList());
    }

    public List<TaintPath> getPathsByVulnerability(VulnerabilityType type) {
        return pathsByVulnerability.getOrDefault(type, Collections.emptyList());
    }

    public List<TaintPath> getPathsBySeverity(Severity severity) {
        return pathsBySeverity.getOrDefault(severity, Collections.emptyList());
    }

    public List<TaintPath> getCriticalPaths() {
        return getPathsBySeverity(Severity.CRITICAL).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
    }

    public List<TaintPath> getHighPaths() {
        return getPathsBySeverity(Severity.HIGH).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
    }

    public boolean hasVulnerabilities() {
        return getUnsanitizedCount() > 0;
    }

    public boolean hasCriticalVulnerabilities() {
        return !getCriticalPaths().isEmpty();
    }

    public Map<VulnerabilityType, Integer> getVulnerabilityCounts() {
        Map<VulnerabilityType, Integer> counts = new EnumMap<>(VulnerabilityType.class);
        for (TaintPath path : getUnsanitizedPaths()) {
            counts.merge(path.getVulnerabilityType(), 1, Integer::sum);
        }
        return counts;
    }

    public Map<Severity, Integer> getSeverityCounts() {
        Map<Severity, Integer> counts = new EnumMap<>(Severity.class);
        for (TaintPath path : getUnsanitizedPaths()) {
            counts.merge(path.getSeverity(), 1, Integer::sum);
        }
        return counts;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Taint Analysis Summary ===\n");
        sb.append(String.format("Total paths found: %d\n", getTotalVulnerabilities()));
        sb.append(String.format("Unsanitized: %d\n", getUnsanitizedCount()));
        sb.append(String.format("Sanitized: %d\n", getSanitizedCount()));
        sb.append("\n");

        Map<Severity, Integer> severityCounts = getSeverityCounts();
        if (!severityCounts.isEmpty()) {
            sb.append("By Severity:\n");
            for (Severity sev : Severity.values()) {
                int count = severityCounts.getOrDefault(sev, 0);
                if (count > 0) {
                    sb.append(String.format("  %s: %d\n", sev, count));
                }
            }
            sb.append("\n");
        }

        Map<VulnerabilityType, Integer> vulnCounts = getVulnerabilityCounts();
        if (!vulnCounts.isEmpty()) {
            sb.append("By Vulnerability Type:\n");
            for (Map.Entry<VulnerabilityType, Integer> entry : vulnCounts.entrySet()) {
                sb.append(String.format("  %s: %d\n", entry.getKey(), entry.getValue()));
            }
        }

        return sb.toString();
    }

    public String getDetailedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(getSummary());
        sb.append("\n");
        sb.append("=== Detailed Findings ===\n\n");

        List<TaintPath> critical = getCriticalPaths();
        if (!critical.isEmpty()) {
            sb.append("--- CRITICAL ---\n");
            for (TaintPath path : critical) {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        List<TaintPath> high = getHighPaths();
        if (!high.isEmpty()) {
            sb.append("--- HIGH ---\n");
            for (TaintPath path : high) {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        List<TaintPath> medium = getPathsBySeverity(Severity.MEDIUM).stream()
            .filter(p -> !p.isSanitized())
            .collect(Collectors.toList());
        if (!medium.isEmpty()) {
            sb.append("--- MEDIUM ---\n");
            for (TaintPath path : medium) {
                sb.append(path.formatPath()).append("\n\n");
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
