package com.tonic.analysis.fingerprint.features;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Level0Features implements FeatureVector {
    private final String returnTypeCategory;
    private final int parameterCount;
    private final List<String> sortedParamCategories;
    private final int exceptionHandlerCount;
    private final int monitorCount;
    private final Set<String> externalCallTargets;
    private final Set<String> fieldAccessTargets;
    private final Set<String> instantiatedTypes;

    public Level0Features(String returnType, int parameterCount,
                          List<String> paramTypes, int exceptionHandlerCount,
                          int monitorCount, Set<String> externalCallTargets,
                          Set<String> fieldAccessTargets, Set<String> instantiatedTypes) {
        this.returnTypeCategory = normalizeType(returnType);
        this.parameterCount = parameterCount;
        this.sortedParamCategories = normalizeAndSort(paramTypes);
        this.exceptionHandlerCount = exceptionHandlerCount;
        this.monitorCount = monitorCount;
        this.externalCallTargets = new TreeSet<>(externalCallTargets);
        this.fieldAccessTargets = new TreeSet<>(fieldAccessTargets);
        this.instantiatedTypes = new TreeSet<>(instantiatedTypes);
    }

    public static String normalizeType(String desc) {
        if (desc == null || desc.isEmpty()) {
            return "V";
        }

        char first = desc.charAt(0);

        if (first == '[') {
            int dims = 0;
            int i = 0;
            while (i < desc.length() && desc.charAt(i) == '[') {
                dims++;
                i++;
            }
            StringBuilder sb = new StringBuilder();
            for (int d = 0; d < dims; d++) {
                sb.append('A');
            }
            if (i < desc.length()) {
                char baseType = desc.charAt(i);
                if (baseType == 'L') {
                    sb.append('O');
                } else if (isPrimitive(baseType)) {
                    sb.append('P');
                } else {
                    sb.append('P');
                }
            }
            return sb.toString();
        }

        if (first == 'L') {
            return "O";
        }

        if (first == 'V') {
            return "V";
        }

        if (isPrimitive(first)) {
            return "P";
        }

        return "P";
    }

    private static boolean isPrimitive(char c) {
        return c == 'I' || c == 'J' || c == 'D' || c == 'F' ||
               c == 'S' || c == 'B' || c == 'C' || c == 'Z';
    }

    private static List<String> normalizeAndSort(List<String> paramTypes) {
        List<String> normalized = new ArrayList<>();
        for (String type : paramTypes) {
            normalized.add(normalizeType(type));
        }
        Collections.sort(normalized);
        return normalized;
    }

    @Override
    public byte[] computeHash() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(returnTypeCategory.getBytes(StandardCharsets.UTF_8));
            md.update((byte) parameterCount);
            for (String t : sortedParamCategories) {
                md.update(t.getBytes(StandardCharsets.UTF_8));
            }
            md.update((byte) exceptionHandlerCount);
            md.update((byte) monitorCount);
            for (String s : externalCallTargets) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
            }
            for (String s : fieldAccessTargets) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
            }
            for (String s : instantiatedTypes) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
            }
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            return new byte[32];
        }
    }

    @Override
    public boolean isValid() {
        return returnTypeCategory != null;
    }

    public double similarity(Level0Features other) {
        if (other == null) {
            return 0.0;
        }

        double score = 0.0;
        double weight = 0.0;

        if (returnTypeCategory.equals(other.returnTypeCategory)) {
            score += 2.0;
        }
        weight += 2.0;

        if (parameterCount == other.parameterCount) {
            score += 2.0;
        }
        weight += 2.0;

        score += jaccard(sortedParamCategories, other.sortedParamCategories) * 1.5;
        weight += 1.5;

        if (exceptionHandlerCount == other.exceptionHandlerCount) {
            score += 1.0;
        }
        weight += 1.0;

        if (monitorCount == other.monitorCount) {
            score += 1.0;
        }
        weight += 1.0;

        score += jaccard(externalCallTargets, other.externalCallTargets) * 2.0;
        weight += 2.0;

        score += jaccard(fieldAccessTargets, other.fieldAccessTargets) * 1.5;
        weight += 1.5;

        score += jaccard(instantiatedTypes, other.instantiatedTypes) * 1.0;
        weight += 1.0;

        return score / weight;
    }

    private double jaccard(Collection<?> a, Collection<?> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<Object> union = new HashSet<>(a);
        union.addAll(b);
        Set<Object> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }

    public String getReturnTypeCategory() {
        return returnTypeCategory;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public List<String> getSortedParamCategories() {
        return Collections.unmodifiableList(sortedParamCategories);
    }

    public int getExceptionHandlerCount() {
        return exceptionHandlerCount;
    }

    public int getMonitorCount() {
        return monitorCount;
    }

    public Set<String> getExternalCallTargets() {
        return Collections.unmodifiableSet(externalCallTargets);
    }

    public Set<String> getFieldAccessTargets() {
        return Collections.unmodifiableSet(fieldAccessTargets);
    }

    public Set<String> getInstantiatedTypes() {
        return Collections.unmodifiableSet(instantiatedTypes);
    }
}
