package com.tonic.analysis.query.planner;

import java.util.Objects;

/**
 * The code location a query match points to: a class, a method, or a specific bytecode offset within
 * a method. A pure location descriptor with no presentation concerns.
 */
public interface QueryTarget
{

    final class MethodTarget implements QueryTarget
    {
        private final String className;
        private final String methodName;
        private final String descriptor;

        /**
         * Creates a target naming one method.
         *
         * @param className the declaring class name
         * @param methodName the method name
         * @param descriptor the method descriptor
         */
        public MethodTarget(String className, String methodName, String descriptor)
        {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
        }

        /**
         * @return the declaring class name
         */
        public String className()
        {
            return className;
        }

        /**
         * @return the method name
         */
        public String methodName()
        {
            return methodName;
        }

        /**
         * @return the method descriptor
         */
        public String descriptor()
        {
            return descriptor;
        }

        /**
         * @return the class, method and descriptor joined into one signature string
         */
        public String getSignature()
        {
            return className + "." + methodName + descriptor;
        }

        /**
         * @param o the object to compare against
         * @return true when o is a MethodTarget with the same class, method name and descriptor
         */
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof MethodTarget)) return false;
            MethodTarget that = (MethodTarget) o;
            return Objects.equals(className, that.className) &&
                   Objects.equals(methodName, that.methodName) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        /**
         * @return a hash over the class, method name and descriptor
         */
        @Override
        public int hashCode()
        {
            return Objects.hash(className, methodName, descriptor);
        }

        /**
         * @return the signature wrapped in a MethodTarget marker
         */
        @Override
        public String toString()
        {
            return "MethodTarget{" + getSignature() + "}";
        }
    }

    final class PCTarget implements QueryTarget
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int pc;

        /**
         * Creates a target naming one bytecode offset inside a method.
         *
         * @param className the declaring class name
         * @param methodName the method name
         * @param descriptor the method descriptor
         * @param pc the bytecode offset within the method
         */
        public PCTarget(String className, String methodName, String descriptor, int pc)
        {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.pc = pc;
        }

        /**
         * @return the declaring class name
         */
        public String className()
        {
            return className;
        }

        /**
         * @return the method name
         */
        public String methodName()
        {
            return methodName;
        }

        /**
         * @return the method descriptor
         */
        public String descriptor()
        {
            return descriptor;
        }

        /**
         * @return the bytecode offset within the method
         */
        public int pc()
        {
            return pc;
        }

        /**
         * @return the method signature with the bytecode offset appended after an "at" sign
         */
        public String getSignature()
        {
            return className + "." + methodName + descriptor + "@" + pc;
        }

        /**
         * @param o the object to compare against
         * @return true when o is a PCTarget with the same method signature and offset
         */
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof PCTarget)) return false;
            PCTarget pcTarget = (PCTarget) o;
            return pc == pcTarget.pc &&
                   Objects.equals(className, pcTarget.className) &&
                   Objects.equals(methodName, pcTarget.methodName) &&
                   Objects.equals(descriptor, pcTarget.descriptor);
        }

        /**
         * @return a hash over the class, method name, descriptor and offset
         */
        @Override
        public int hashCode()
        {
            return Objects.hash(className, methodName, descriptor, pc);
        }

        /**
         * @return the signature and offset wrapped in a PCTarget marker
         */
        @Override
        public String toString()
        {
            return "PCTarget{" + getSignature() + "}";
        }
    }

    final class ClassTarget implements QueryTarget
    {
        private final String className;

        /**
         * Creates a target naming one class.
         *
         * @param className the class name
         */
        public ClassTarget(String className)
        {
            this.className = className;
        }

        /**
         * @return the class name
         */
        public String className()
        {
            return className;
        }

        /**
         * @param o the object to compare against
         * @return true when o is a ClassTarget naming the same class
         */
        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof ClassTarget)) return false;
            ClassTarget that = (ClassTarget) o;
            return Objects.equals(className, that.className);
        }

        /**
         * @return a hash over the class name
         */
        @Override
        public int hashCode()
        {
            return Objects.hash(className);
        }

        /**
         * @return the class name wrapped in a ClassTarget marker
         */
        @Override
        public String toString()
        {
            return "ClassTarget{" + className + "}";
        }
    }
}
