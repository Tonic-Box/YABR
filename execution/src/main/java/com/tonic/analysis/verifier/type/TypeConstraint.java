package com.tonic.analysis.verifier.type;

import com.tonic.analysis.frame.VerificationType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;

import java.util.HashSet;
import java.util.Set;

/**
 * Assignability and category rules over verification types; class hierarchy
 * questions are answered from a class pool, and without one every reference
 * relation is assumed to hold.
 */
public class TypeConstraint
{
    private final ClassPool classPool;

    /**
     * Creates a constraint checker.
     * @param classPool the pool used to walk superclass chains, or null to assume
     *                  every reference relation holds
     */
    public TypeConstraint(ClassPool classPool)
    {
        this.classPool = classPool;
    }

    /**
     * Decides whether a value of one type may be stored where another is expected.
     * Null is assignable to any reference, and everything is assignable to top.
     * @param source the type being supplied
     * @param target the type being expected
     * @return true if the assignment is allowed, false if either type is null
     */
    public boolean isAssignableTo(VerificationType source, VerificationType target)
    {
        if (source == null || target == null)
        {
            return false;
        }

        if (source.equals(target))
        {
            return true;
        }

        if (target.equals(VerificationType.TOP))
        {
            return true;
        }

        if (source.equals(VerificationType.NULL))
        {
            return isReferenceType(target);
        }

        if (isPrimitiveType(source) && isPrimitiveType(target))
        {
            return isCompatiblePrimitive(source, target);
        }

        if (isReferenceType(source) && isReferenceType(target))
        {
            return isCompatibleReference(source, target);
        }

        return false;
    }

    /**
     * Checks an array load, which only requires the array operand to be a reference;
     * the element type is not currently constrained.
     * @param arrayType the type of the array operand
     * @param expectedElement the element type the load opcode expects
     * @return true if the array operand is a reference
     */
    public boolean isArrayLoadValid(VerificationType arrayType, VerificationType expectedElement)
    {
        if (!isReferenceType(arrayType))
        {
            return false;
        }

        return true;
    }

    /**
     * Checks an invocation receiver, accepting null and uninitialized references;
     * the owner is not currently constrained.
     * @param receiver the type on the stack in receiver position
     * @param expectedOwner internal name of the class declaring the callee
     * @return true if the receiver is a reference
     */
    public boolean isReceiverValid(VerificationType receiver, String expectedOwner)
    {
        if (receiver.equals(VerificationType.NULL))
        {
            return true;
        }

        if (receiver.equals(VerificationType.UNINITIALIZED_THIS))
        {
            return true;
        }

        if (receiver instanceof VerificationType.UninitializedType)
        {
            return true;
        }

        return isReferenceType(receiver);
    }

    /**
     * Decides whether a value may be thrown, walking to Throwable when a class
     * pool is available and accepting the type otherwise.
     * @param type the type on the stack
     * @return true if the value may be thrown
     */
    public boolean isThrowable(VerificationType type)
    {
        if (type.equals(VerificationType.NULL))
        {
            return true;
        }

        if (!isReferenceType(type))
        {
            return false;
        }

        if (classPool == null)
        {
            return true;
        }

        if (type instanceof VerificationType.ObjectType)
        {
            VerificationType.ObjectType objType = (VerificationType.ObjectType) type;
            String className = objType.getClassName();
            if (className == null)
            {
                return true;
            }

            return isSubclassOf(className, "java/lang/Throwable");
        }

        return true;
    }

    /**
     * @param type the type to test
     * @return true if the type is int
     */
    public boolean isInteger(VerificationType type)
    {
        return type.equals(VerificationType.INTEGER);
    }

    /**
     * @param type the type to test
     * @return true if the type is long
     */
    public boolean isLong(VerificationType type)
    {
        return type.equals(VerificationType.LONG);
    }

    /**
     * @param type the type to test
     * @return true if the type is float
     */
    public boolean isFloat(VerificationType type)
    {
        return type.equals(VerificationType.FLOAT);
    }

    /**
     * @param type the type to test
     * @return true if the type is double
     */
    public boolean isDouble(VerificationType type)
    {
        return type.equals(VerificationType.DOUBLE);
    }

    /**
     * @param type the type to test
     * @return true if the type takes one stack slot
     */
    public boolean isCategory1(VerificationType type)
    {
        return !type.isTwoSlot();
    }

    /**
     * @param type the type to test
     * @return true if the type takes two stack slots
     */
    public boolean isCategory2(VerificationType type)
    {
        return type.isTwoSlot();
    }

    /**
     * @param type the type to test
     * @return true if the type is a reference, including null and uninitialized ones
     */
    public boolean isReferenceType(VerificationType type)
    {
        if (type.equals(VerificationType.NULL))
        {
            return true;
        }
        if (type.equals(VerificationType.UNINITIALIZED_THIS))
        {
            return true;
        }
        if (type instanceof VerificationType.ObjectType)
        {
            return true;
        }
        if (type instanceof VerificationType.UninitializedType)
        {
            return true;
        }
        return false;
    }

    /**
     * @param type the type to test
     * @return true for int, long, float, double or top
     */
    public boolean isPrimitiveType(VerificationType type)
    {
        return type.equals(VerificationType.INTEGER) ||
               type.equals(VerificationType.LONG) ||
               type.equals(VerificationType.FLOAT) ||
               type.equals(VerificationType.DOUBLE) ||
               type.equals(VerificationType.TOP);
    }

    private boolean isCompatiblePrimitive(VerificationType source, VerificationType target)
    {
        if (source.equals(VerificationType.INTEGER) && target.equals(VerificationType.INTEGER))
        {
            return true;
        }
        if (source.equals(VerificationType.LONG) && target.equals(VerificationType.LONG))
        {
            return true;
        }
        if (source.equals(VerificationType.FLOAT) && target.equals(VerificationType.FLOAT))
        {
            return true;
        }
        if (source.equals(VerificationType.DOUBLE) && target.equals(VerificationType.DOUBLE))
        {
            return true;
        }
        return false;
    }

    private boolean isCompatibleReference(VerificationType source, VerificationType target)
    {
        if (source.equals(VerificationType.NULL))
        {
            return true;
        }

        if (!(source instanceof VerificationType.ObjectType) || !(target instanceof VerificationType.ObjectType))
        {
            return source.equals(target);
        }

        VerificationType.ObjectType sourceObj = (VerificationType.ObjectType) source;
        VerificationType.ObjectType targetObj = (VerificationType.ObjectType) target;

        String sourceName = sourceObj.getClassName();
        String targetName = targetObj.getClassName();

        if (sourceName == null || targetName == null)
        {
            return true;
        }

        if (sourceName.equals(targetName))
        {
            return true;
        }

        if (targetName.equals("java/lang/Object"))
        {
            return true;
        }

        if (classPool != null)
        {
            return isSubclassOf(sourceName, targetName);
        }

        return true;
    }

    private boolean isSubclassOf(String className, String superClassName)
    {
        if (className == null || superClassName == null)
        {
            return true;
        }

        if (className.equals(superClassName))
        {
            return true;
        }

        if (classPool == null)
        {
            return true;
        }

        String currentClass = className;
        Set<String> visited = new HashSet<>();

        while (currentClass != null && !visited.contains(currentClass))
        {
            visited.add(currentClass);

            if (currentClass.equals(superClassName))
            {
                return true;
            }

            ClassFile cf = classPool.get(currentClass);
            if (cf == null)
            {
                return true;
            }

            currentClass = cf.getSuperClassName();
        }

        return false;
    }
}
