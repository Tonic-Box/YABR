package com.tonic.analysis.query.planner;

import java.util.Objects;

/**
 * The code location a query match points to: a class, a method, or a specific bytecode offset within
 * a method. A pure location descriptor with no presentation concerns.
 */
public interface QueryTarget {

    final class MethodTarget implements QueryTarget {
        private final String className;
        private final String methodName;
        private final String descriptor;

        public MethodTarget(String className, String methodName, String descriptor) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        public String className() {
            return className;
        }

        public String methodName() {
            return methodName;
        }

        public String descriptor() {
            return descriptor;
        }

        public String getSignature() {
            return className + "." + methodName + descriptor;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MethodTarget)) return false;
            MethodTarget that = (MethodTarget) o;
            return Objects.equals(className, that.className) &&
                   Objects.equals(methodName, that.methodName) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, descriptor);
        }

        @Override
        public String toString() {
            return "MethodTarget{" + getSignature() + "}";
        }
    }

    final class PCTarget implements QueryTarget {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;

        public PCTarget(String className, String methodName, String descriptor, int pc) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
        }

        public String className() {
            return className;
        }

        public String methodName() {
            return methodName;
        }

        public String descriptor() {
            return descriptor;
        }

        public int pc() {
            return pc;
        }

        public String getSignature() {
            return className + "." + methodName + descriptor + "@" + pc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PCTarget)) return false;
            PCTarget pcTarget = (PCTarget) o;
            return pc == pcTarget.pc &&
                   Objects.equals(className, pcTarget.className) &&
                   Objects.equals(methodName, pcTarget.methodName) &&
                   Objects.equals(descriptor, pcTarget.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, methodName, descriptor, pc);
        }

        @Override
        public String toString() {
            return "PCTarget{" + getSignature() + "}";
        }
    }

    final class ClassTarget implements QueryTarget {
        private final String className;

        public ClassTarget(String className) {
            this.className = className;
        }

        public String className() {
            return className;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ClassTarget)) return false;
            ClassTarget that = (ClassTarget) o;
            return Objects.equals(className, that.className);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className);
        }

        @Override
        public String toString() {
            return "ClassTarget{" + className + "}";
        }
    }
}
