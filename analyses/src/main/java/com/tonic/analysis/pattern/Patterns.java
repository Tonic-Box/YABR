package com.tonic.analysis.pattern;

import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.Value;

import java.util.regex.Pattern;

/**
 * Factory for common pattern matchers.
 */
public final class Patterns
{

    private Patterns() {} // Utility class

    // Method Call Patterns

    /**
     * Matches any method call.
     *
     * @return a matcher accepting every invoke instruction
     */
    public static PatternMatcher anyMethodCall()
    {
        return (instr, method, sourceMethod, classFile) -> instr instanceof InvokeInstruction;
    }

    /**
     * Matches method calls to a specific owner class.
     *
     * @param ownerClass the owner name an invoke must declare
     * @return a matcher accepting invokes on that owner
     */
    public static PatternMatcher methodCallTo(String ownerClass)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return ownerClass.equals(invoke.getOwner());
        };
    }

    /**
     * Matches method calls with a specific name.
     *
     * @param methodName the method name an invoke must carry
     * @return a matcher accepting invokes with that name
     */
    public static PatternMatcher methodCallNamed(String methodName)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return methodName.equals(invoke.getName());
        };
    }

    /**
     * Matches method calls to a specific owner and method name.
     *
     * @param ownerClass the owner name an invoke must declare
     * @param methodName the method name an invoke must carry
     * @return a matcher accepting invokes matching both
     */
    public static PatternMatcher methodCall(String ownerClass, String methodName)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return ownerClass.equals(invoke.getOwner()) && methodName.equals(invoke.getName());
        };
    }

    /**
     * Matches method calls with owner matching a regex pattern.
     *
     * @param regex the expression the whole owner name must match
     * @return a matcher accepting invokes whose owner matches
     */
    public static PatternMatcher methodCallOwnerMatching(String regex)
    {
        Pattern pattern = Pattern.compile(regex);
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getOwner() != null && pattern.matcher(invoke.getOwner()).matches();
        };
    }

    /**
     * Matches method calls with name matching a regex pattern.
     *
     * @param regex the expression the whole method name must match
     * @return a matcher accepting invokes whose name matches
     */
    public static PatternMatcher methodCallNameMatching(String regex)
    {
        Pattern pattern = Pattern.compile(regex);
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getName() != null && pattern.matcher(invoke.getName()).matches();
        };
    }

    /**
     * Matches static method calls.
     *
     * @return a matcher accepting invokestatic instructions
     */
    public static PatternMatcher staticMethodCall()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.STATIC;
        };
    }

    /**
     * Matches virtual and interface method calls.
     *
     * @return a matcher accepting invokevirtual and invokeinterface instructions
     */
    public static PatternMatcher virtualMethodCall()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.VIRTUAL ||
                   invoke.getInvokeType() == InvokeType.INTERFACE;
        };
    }

    /**
     * Matches invokedynamic calls.
     *
     * @return a matcher accepting invokedynamic instructions
     */
    public static PatternMatcher dynamicCall()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.DYNAMIC;
        };
    }

    // Field Access Patterns

    /**
     * Matches any field read.
     *
     * @return a matcher accepting loading field accesses
     */
    public static PatternMatcher anyFieldRead()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof FieldAccessInstruction)
            {
                return ((FieldAccessInstruction) instr).isLoad();
            }
            return false;
        };
    }

    /**
     * Matches any field write.
     *
     * @return a matcher accepting storing field accesses
     */
    public static PatternMatcher anyFieldWrite()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof FieldAccessInstruction)
            {
                return ((FieldAccessInstruction) instr).isStore();
            }
            return false;
        };
    }

    /**
     * Matches field access (read or write) on a specific owner.
     *
     * @param ownerClass the owner name a field access must declare
     * @return a matcher accepting field accesses on that owner
     */
    public static PatternMatcher fieldAccessOn(String ownerClass)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof FieldAccessInstruction)
            {
                return ownerClass.equals(((FieldAccessInstruction) instr).getOwner());
            }
            return false;
        };
    }

    /**
     * Matches field access with a specific field name.
     *
     * @param fieldName the field name an access must carry
     * @return a matcher accepting field accesses with that name
     */
    public static PatternMatcher fieldNamed(String fieldName)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof FieldAccessInstruction)
            {
                return fieldName.equals(((FieldAccessInstruction) instr).getName());
            }
            return false;
        };
    }

    // Type Check Patterns

    /**
     * Matches instanceof checks.
     *
     * @return a matcher accepting instanceof type checks
     */
    public static PatternMatcher anyInstanceOf()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof TypeCheckInstruction)
            {
                return ((TypeCheckInstruction) instr).isInstanceOf();
            }
            return false;
        };
    }

    /**
     * Matches instanceof checks for a specific type.
     *
     * @param typeName the internal name the checked reference type must have
     * @return a matcher accepting instanceof checks against that type
     */
    public static PatternMatcher instanceOf(String typeName)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof TypeCheckInstruction))
            {
                return false;
            }
            TypeCheckInstruction tc = (TypeCheckInstruction) instr;
            if (!tc.isInstanceOf())
            {
                return false;
            }
            IRType checkType = tc.getTargetType();
            if (checkType instanceof ReferenceType)
            {
                return typeName.equals(((ReferenceType) checkType).getInternalName());
            }
            return false;
        };
    }

    /**
     * Matches cast instructions.
     *
     * @return a matcher accepting checkcast type checks
     */
    public static PatternMatcher anyCast()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof TypeCheckInstruction)
            {
                return ((TypeCheckInstruction) instr).isCast();
            }
            return false;
        };
    }

    /**
     * Matches casts to a specific type.
     *
     * @param typeName the internal name the cast target reference type must have
     * @return a matcher accepting casts to that type
     */
    public static PatternMatcher castTo(String typeName)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof TypeCheckInstruction))
            {
                return false;
            }
            TypeCheckInstruction tc = (TypeCheckInstruction) instr;
            if (!tc.isCast())
            {
                return false;
            }
            IRType targetType = tc.getTargetType();
            if (targetType instanceof ReferenceType)
            {
                return typeName.equals(((ReferenceType) targetType).getInternalName());
            }
            return false;
        };
    }

    // Object Creation Patterns

    /**
     * Matches any object allocation.
     *
     * @return a matcher accepting new instructions
     */
    public static PatternMatcher anyNew()
    {
        return (instr, method, sourceMethod, classFile) -> instr instanceof NewInstruction;
    }

    /**
     * Matches allocation of a specific class.
     *
     * @param className the allocated class name to match
     * @return a matcher accepting allocations of that class
     */
    public static PatternMatcher newInstance(String className)
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof NewInstruction)) return false;
            NewInstruction ni = (NewInstruction) instr;
            return className.equals(ni.getClassName());
        };
    }

    /**
     * Matches any array allocation.
     *
     * @return a matcher accepting array allocation instructions
     */
    public static PatternMatcher anyNewArray()
    {
        return (instr, method, sourceMethod, classFile) -> instr instanceof NewArrayInstruction;
    }

    // Control Flow Patterns

    /**
     * Matches null comparisons in branches.
     *
     * @return a matcher accepting equality branches with a null operand
     */
    public static PatternMatcher nullCheck()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof BranchInstruction)) return false;
            BranchInstruction branch = (BranchInstruction) instr;
            CompareOp condition = branch.getCondition();
            if (condition != CompareOp.EQ && condition != CompareOp.NE) return false;
            Value left = branch.getLeft();
            Value right = branch.getRight();
            return (left instanceof NullConstant) || (right instanceof NullConstant);
        };
    }

    /**
     * Matches throw instructions.
     *
     * @return a matcher accepting athrow instructions
     */
    public static PatternMatcher anyThrow()
    {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof SimpleInstruction)
            {
                return ((SimpleInstruction) instr).getOp() == SimpleOp.ATHROW;
            }
            return false;
        };
    }

    /**
     * Matches return instructions.
     *
     * @return a matcher accepting return instructions
     */
    public static PatternMatcher anyReturn()
    {
        return (instr, method, sourceMethod, classFile) -> instr instanceof ReturnInstruction;
    }

    // Combination Patterns

    /**
     * Combines patterns with AND logic.
     *
     * @param matchers the matchers that must all accept
     * @return a matcher accepting only when every operand accepts
     */
    public static PatternMatcher and(PatternMatcher... matchers)
    {
        return (instr, method, sourceMethod, classFile) -> {
            for (PatternMatcher matcher : matchers)
            {
                if (!matcher.matches(instr, method, sourceMethod, classFile))
                {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Combines patterns with OR logic.
     *
     * @param matchers the matchers of which one must accept
     * @return a matcher accepting when any operand accepts
     */
    public static PatternMatcher or(PatternMatcher... matchers)
    {
        return (instr, method, sourceMethod, classFile) -> {
            for (PatternMatcher matcher : matchers)
            {
                if (matcher.matches(instr, method, sourceMethod, classFile))
                {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Negates a pattern.
     *
     * @param matcher the matcher to invert
     * @return a matcher accepting exactly what the operand rejects
     */
    public static PatternMatcher not(PatternMatcher matcher)
    {
        return (instr, method, sourceMethod, classFile) ->
            !matcher.matches(instr, method, sourceMethod, classFile);
    }
}
