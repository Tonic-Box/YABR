package com.tonic.analysis.fingerprint;

import java.util.*;

public class FingerprintDatabase {
    private final Map<String, MethodFingerprint> fingerprintsByMethod;
    private final Map<ByteArrayWrapper, List<String>> level0Index;
    private final Map<ByteArrayWrapper, List<String>> level1Index;
    private final Map<ByteArrayWrapper, List<String>> level2Index;
    private final FingerprintComparator comparator;

    public FingerprintDatabase() {
        this.fingerprintsByMethod = new HashMap<>();
        this.level0Index = new HashMap<>();
        this.level1Index = new HashMap<>();
        this.level2Index = new HashMap<>();
        this.comparator = new FingerprintComparator();
    }

    public void add(MethodFingerprint fp) {
        if (fp == null || fp.getMethodId() == null) {
            return;
        }

        fingerprintsByMethod.put(fp.getMethodId(), fp);

        if (fp.hasLevel(FingerprintLevel.ULTRA_STABLE)) {
            ByteArrayWrapper key = new ByteArrayWrapper(fp.getHash(FingerprintLevel.ULTRA_STABLE));
            level0Index.computeIfAbsent(key, k -> new ArrayList<>()).add(fp.getMethodId());
        }
        if (fp.hasLevel(FingerprintLevel.STABLE)) {
            ByteArrayWrapper key = new ByteArrayWrapper(fp.getHash(FingerprintLevel.STABLE));
            level1Index.computeIfAbsent(key, k -> new ArrayList<>()).add(fp.getMethodId());
        }
        if (fp.hasLevel(FingerprintLevel.DETAILED)) {
            ByteArrayWrapper key = new ByteArrayWrapper(fp.getHash(FingerprintLevel.DETAILED));
            level2Index.computeIfAbsent(key, k -> new ArrayList<>()).add(fp.getMethodId());
        }
    }

    public void addAll(Collection<MethodFingerprint> fingerprints) {
        for (MethodFingerprint fp : fingerprints) {
            add(fp);
        }
    }

    public List<String> findExactMatches(MethodFingerprint query, FingerprintLevel level) {
        byte[] hash = query.getHash(level);
        if (hash == null) {
            return Collections.emptyList();
        }

        Map<ByteArrayWrapper, List<String>> index = getIndexForLevel(level);
        List<String> matches = index.get(new ByteArrayWrapper(hash));
        return matches != null ? new ArrayList<>(matches) : Collections.emptyList();
    }

    public List<FingerprintMatch> findSimilar(MethodFingerprint query, double threshold) {
        List<String> candidates = findCandidates(query);
        List<FingerprintMatch> matches = new ArrayList<>();

        for (String methodId : candidates) {
            if (methodId.equals(query.getMethodId())) {
                continue;
            }

            MethodFingerprint fp = fingerprintsByMethod.get(methodId);
            if (fp == null) {
                continue;
            }

            FingerprintMatch match = comparator.compare(query, fp);
            if (match.getScore() >= threshold) {
                matches.add(match);
            }
        }

        matches.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return matches;
    }

    public List<FingerprintMatch> findTopMatches(MethodFingerprint query, int limit) {
        List<FingerprintMatch> allMatches = findSimilar(query, 0.0);
        if (allMatches.size() <= limit) {
            return allMatches;
        }
        return allMatches.subList(0, limit);
    }

    public FingerprintMatch findBestMatch(MethodFingerprint query) {
        List<FingerprintMatch> matches = findTopMatches(query, 1);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private List<String> findCandidates(MethodFingerprint query) {
        Set<String> candidates = new LinkedHashSet<>();

        List<String> l0Matches = findExactMatches(query, FingerprintLevel.ULTRA_STABLE);
        if (!l0Matches.isEmpty()) {
            candidates.addAll(l0Matches);
            return new ArrayList<>(candidates);
        }

        List<String> l1Matches = findExactMatches(query, FingerprintLevel.STABLE);
        candidates.addAll(l1Matches);

        if (candidates.size() < 100) {
            candidates.addAll(fingerprintsByMethod.keySet());
        }

        return new ArrayList<>(candidates);
    }

    private Map<ByteArrayWrapper, List<String>> getIndexForLevel(FingerprintLevel level) {
        switch (level) {
            case ULTRA_STABLE: return level0Index;
            case STABLE: return level1Index;
            case DETAILED: return level2Index;
            default: return level0Index;
        }
    }

    public MethodFingerprint get(String methodId) {
        return fingerprintsByMethod.get(methodId);
    }

    public boolean contains(String methodId) {
        return fingerprintsByMethod.containsKey(methodId);
    }

    public int size() {
        return fingerprintsByMethod.size();
    }

    public Collection<MethodFingerprint> getAllFingerprints() {
        return Collections.unmodifiableCollection(fingerprintsByMethod.values());
    }

    public void clear() {
        fingerprintsByMethod.clear();
        level0Index.clear();
        level1Index.clear();
        level2Index.clear();
    }

    private static class ByteArrayWrapper {
        private final byte[] data;
        private final int hashCode;

        ByteArrayWrapper(byte[] data) {
            this.data = data != null ? data.clone() : new byte[0];
            this.hashCode = Arrays.hashCode(this.data);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ByteArrayWrapper)) return false;
            return Arrays.equals(data, ((ByteArrayWrapper) o).data);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
