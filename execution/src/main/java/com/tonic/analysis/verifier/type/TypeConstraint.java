package com.tonic.analysis.verifier.type;

import com.tonic.analysis.frame.VerificationType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.util.HashSet;
import java.util.Set;

public class TypeConstraint {
    private final ClassPool classPool;

    public TypeConstraint(ClassPool classPool) {
        this.classPool = classPool;
    }

    public boolean isAssignableTo(VerificationType source, VerificationType target) {
        if (source == null || target == null) {
            return false;
        }

        if (source.equals(target)) {
            return true;
        }

        if (target.equals(VerificationType.TOP)) {
            return true;
        }

        if (source.equals(VerificationType.NULL)) {
            return isReferenceType(target);
        }

        if (isPrimitiveType(source) && isPrimitiveType(target)) {
            return isCompatiblePrimitive(source, target);
        }

        if (isReferenceType(source) && isReferenceType(target)) {
            return isCompatibleReference(source, target);
        }

        return false;
    }

    public boolean isArrayLoadValid(VerificationType arrayType, VerificationType expectedElement) {
        if (!isReferenceType(arrayType)) {
            return false;
        }

        return true;
    }

    public boolean isReceiverValid(VerificationType receiver, String expectedOwner) {
        if (receiver.equals(VerificationType.NULL)) {
            return true;
        }

        if (receiver.equals(VerificationType.UNINITIALIZED_THIS)) {
            return true;
        }

        if (receiver instanceof VerificationType.UninitializedType) {
            return true;
        }

        return isReferenceType(receiver);
    }

    public boolean isThrowable(VerificationType type) {
        if (type.equals(VerificationType.NULL)) {
            return true;
        }

        if (!isReferenceType(type)) {
            return false;
        }

        if (classPool == null) {
            return true;
        }

        if (type instanceof VerificationType.ObjectType) {
            VerificationType.ObjectType objType = (VerificationType.ObjectType) type;
            String className = objType.getClassName();
            if (className == null) {
                return true;
            }

            return isSubclassOf(className, "java/lang/Throwable");
        }

        return true;
    }

    public boolean isInteger(VerificationType type) {
        return type.equals(VerificationType.INTEGER);
    }

    public boolean isLong(VerificationType type) {
        return type.equals(VerificationType.LONG);
    }

    public boolean isFloat(VerificationType type) {
        return type.equals(VerificationType.FLOAT);
    }

    public boolean isDouble(VerificationType type) {
        return type.equals(VerificationType.DOUBLE);
    }

    public boolean isCategory1(VerificationType type) {
        return !type.isTwoSlot();
    }

    public boolean isCategory2(VerificationType type) {
        return type.isTwoSlot();
    }

    public boolean isReferenceType(VerificationType type) {
        if (type.equals(VerificationType.NULL)) {
            return true;
        }
        if (type.equals(VerificationType.UNINITIALIZED_THIS)) {
            return true;
        }
        if (type instanceof VerificationType.ObjectType) {
            return true;
        }
        if (type instanceof VerificationType.UninitializedType) {
            return true;
        }
        return false;
    }

    public boolean isPrimitiveType(VerificationType type) {
        return type.equals(VerificationType.INTEGER) ||
               type.equals(VerificationType.LONG) ||
               type.equals(VerificationType.FLOAT) ||
               type.equals(VerificationType.DOUBLE) ||
               type.equals(VerificationType.TOP);
    }

    private boolean isCompatiblePrimitive(VerificationType source, VerificationType target) {
        if (source.equals(VerificationType.INTEGER) && target.equals(VerificationType.INTEGER)) {
            return true;
        }
        if (source.equals(VerificationType.LONG) && target.equals(VerificationType.LONG)) {
            return true;
        }
        if (source.equals(VerificationType.FLOAT) && target.equals(VerificationType.FLOAT)) {
            return true;
        }
        if (source.equals(VerificationType.DOUBLE) && target.equals(VerificationType.DOUBLE)) {
            return true;
        }
        return false;
    }

    private boolean isCompatibleReference(VerificationType source, VerificationType target) {
        if (source.equals(VerificationType.NULL)) {
            return true;
        }

        if (!(source instanceof VerificationType.ObjectType) ||
            !(target instanceof VerificationType.ObjectType)) {
            return source.equals(target);
        }

        VerificationType.ObjectType sourceObj = (VerificationType.ObjectType) source;
        VerificationType.ObjectType targetObj = (VerificationType.ObjectType) target;

        String sourceName = sourceObj.getClassName();
        String targetName = targetObj.getClassName();

        if (sourceName == null || targetName == null) {
            return true;
        }

        if (sourceName.equals(targetName)) {
            return true;
        }

        if (targetName.equals("java/lang/Object")) {
            return true;
        }

        if (classPool != null) {
            return isSubclassOf(sourceName, targetName);
        }

        return true;
    }

    private boolean isSubclassOf(String className, String superClassName) {
        if (className == null || superClassName == null) {
            return true;
        }

        if (className.equals(superClassName)) {
            return true;
        }

        if (classPool == null) {
            return true;
        }

        String currentClass = className;
        Set<String> visited = new HashSet<>();

        while (currentClass != null && !visited.contains(currentClass)) {
            visited.add(currentClass);

            if (currentClass.equals(superClassName)) {
                return true;
            }

            ClassFile cf = classPool.get(currentClass);
            if (cf == null) {
                return true;
            }

            currentClass = cf.getSuperClassName();
        }

        return false;
    }
}
