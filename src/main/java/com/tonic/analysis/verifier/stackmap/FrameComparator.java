package com.tonic.analysis.verifier.stackmap;

import com.tonic.analysis.frame.TypeState;
import com.tonic.analysis.frame.VerificationType;

import java.util.ArrayList;
import java.util.List;

public class FrameComparator {

    public List<FrameMismatch> compare(TypeState declared, TypeState computed) {
        List<FrameMismatch> mismatches = new ArrayList<>();

        compareLocals(declared, computed, mismatches);
        compareStack(declared, computed, mismatches);

        return mismatches;
    }

    private void compareLocals(TypeState declared, TypeState computed, List<FrameMismatch> mismatches) {
        int declaredCount = declared.getLocalsCount();
        int computedCount = computed.getLocalsCount();

        int maxCount = Math.max(declaredCount, computedCount);

        for (int i = 0; i < maxCount; i++) {
            VerificationType declaredType = i < declaredCount ? declared.getLocal(i) : VerificationType.TOP;
            VerificationType computedType = i < computedCount ? computed.getLocal(i) : VerificationType.TOP;

            if (!typesCompatible(declaredType, computedType)) {
                mismatches.add(new FrameMismatch(
                        FrameMismatch.Location.LOCALS,
                        i,
                        declaredType,
                        computedType,
                        "Local variable " + i + " type mismatch: declared=" + declaredType +
                        ", computed=" + computedType,
                        false
                ));
            }
        }
    }

    private void compareStack(TypeState declared, TypeState computed, List<FrameMismatch> mismatches) {
        int declaredSize = declared.getStackSize();
        int computedSize = computed.getStackSize();

        if (declaredSize != computedSize) {
            mismatches.add(new FrameMismatch(
                    FrameMismatch.Location.STACK,
                    -1,
                    null,
                    null,
                    "Stack depth mismatch: declared=" + declaredSize + ", computed=" + computedSize,
                    true
            ));
            return;
        }

        for (int i = 0; i < declaredSize; i++) {
            VerificationType declaredType = declared.getStack().get(i);
            VerificationType computedType = computed.getStack().get(i);

            if (!typesCompatible(declaredType, computedType)) {
                mismatches.add(new FrameMismatch(
                        FrameMismatch.Location.STACK,
                        i,
                        declaredType,
                        computedType,
                        "Stack slot " + i + " type mismatch: declared=" + declaredType +
                        ", computed=" + computedType,
                        true
                ));
            }
        }
    }

    private boolean typesCompatible(VerificationType declared, VerificationType computed) {
        if (declared == null || computed == null) {
            return declared == computed;
        }

        if (declared.equals(computed)) {
            return true;
        }

        if (declared.equals(VerificationType.TOP) || computed.equals(VerificationType.TOP)) {
            return true;
        }

        if (declared.equals(VerificationType.NULL) && isReferenceType(computed)) {
            return true;
        }
        if (computed.equals(VerificationType.NULL) && isReferenceType(declared)) {
            return true;
        }

        if (declared instanceof VerificationType.ObjectType && computed instanceof VerificationType.ObjectType) {
            return true;
        }

        return false;
    }

    private boolean isReferenceType(VerificationType type) {
        return type.equals(VerificationType.NULL) ||
               type.equals(VerificationType.UNINITIALIZED_THIS) ||
               type instanceof VerificationType.ObjectType ||
               type instanceof VerificationType.UninitializedType;
    }

    public static class FrameMismatch {
        public enum Location {
            LOCALS,
            STACK
        }

        private final Location location;
        private final int index;
        private final VerificationType declared;
        private final VerificationType computed;
        private final String description;
        private final boolean critical;

        public FrameMismatch(Location location, int index, VerificationType declared,
                             VerificationType computed, String description, boolean critical) {
            this.location = location;
            this.index = index;
            this.declared = declared;
            this.computed = computed;
            this.description = description;
            this.critical = critical;
        }

        public Location getLocation() {
            return location;
        }

        public int getIndex() {
            return index;
        }

        public VerificationType getDeclared() {
            return declared;
        }

        public VerificationType getComputed() {
            return computed;
        }

        public String getDescription() {
            return description;
        }

        public boolean isCritical() {
            return critical;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
