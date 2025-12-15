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
public final class Patterns {

    private Patterns() {} // Utility class

    // ===== Method Call Patterns =====

    /**
     * Matches any method call.
     */
    public static PatternMatcher anyMethodCall() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof InvokeInstruction;
    }

    /**
     * Matches method calls to a specific owner class.
     */
    public static PatternMatcher methodCallTo(String ownerClass) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return ownerClass.equals(invoke.getOwner());
        };
    }

    /**
     * Matches method calls with a specific name.
     */
    public static PatternMatcher methodCallNamed(String methodName) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return methodName.equals(invoke.getName());
        };
    }

    /**
     * Matches method calls to a specific owner and method name.
     */
    public static PatternMatcher methodCall(String ownerClass, String methodName) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return ownerClass.equals(invoke.getOwner()) && methodName.equals(invoke.getName());
        };
    }

    /**
     * Matches method calls with owner matching a regex pattern.
     */
    public static PatternMatcher methodCallOwnerMatching(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getOwner() != null && pattern.matcher(invoke.getOwner()).matches();
        };
    }

    /**
     * Matches method calls with name matching a regex pattern.
     */
    public static PatternMatcher methodCallNameMatching(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getName() != null && pattern.matcher(invoke.getName()).matches();
        };
    }

    /**
     * Matches static method calls.
     */
    public static PatternMatcher staticMethodCall() {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.STATIC;
        };
    }

    /**
     * Matches virtual/interface method calls.
     */
    public static PatternMatcher virtualMethodCall() {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.VIRTUAL ||
                   invoke.getInvokeType() == InvokeType.INTERFACE;
        };
    }

    /**
     * Matches invokedynamic calls.
     */
    public static PatternMatcher dynamicCall() {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InvokeInstruction)) return false;
            InvokeInstruction invoke = (InvokeInstruction) instr;
            return invoke.getInvokeType() == InvokeType.DYNAMIC;
        };
    }

    // ===== Field Access Patterns =====

    /**
     * Matches any field read.
     */
    public static PatternMatcher anyFieldRead() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof GetFieldInstruction;
    }

    /**
     * Matches any field write.
     */
    public static PatternMatcher anyFieldWrite() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof PutFieldInstruction;
    }

    /**
     * Matches field access (read or write) on a specific owner.
     */
    public static PatternMatcher fieldAccessOn(String ownerClass) {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof GetFieldInstruction) {
                return ownerClass.equals(((GetFieldInstruction) instr).getOwner());
            }
            if (instr instanceof PutFieldInstruction) {
                return ownerClass.equals(((PutFieldInstruction) instr).getOwner());
            }
            return false;
        };
    }

    /**
     * Matches field access with a specific field name.
     */
    public static PatternMatcher fieldNamed(String fieldName) {
        return (instr, method, sourceMethod, classFile) -> {
            if (instr instanceof GetFieldInstruction) {
                return fieldName.equals(((GetFieldInstruction) instr).getName());
            }
            if (instr instanceof PutFieldInstruction) {
                return fieldName.equals(((PutFieldInstruction) instr).getName());
            }
            return false;
        };
    }

    // ===== Type Check Patterns =====

    /**
     * Matches instanceof checks.
     */
    public static PatternMatcher anyInstanceOf() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof InstanceOfInstruction;
    }

    /**
     * Matches instanceof checks for a specific type.
     */
    public static PatternMatcher instanceOf(String typeName) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof InstanceOfInstruction)) return false;
            InstanceOfInstruction iof = (InstanceOfInstruction) instr;
            IRType checkType = iof.getCheckType();
            if (checkType instanceof ReferenceType) {
                return typeName.equals(((ReferenceType) checkType).getInternalName());
            }
            return false;
        };
    }

    /**
     * Matches cast instructions.
     */
    public static PatternMatcher anyCast() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof CastInstruction;
    }

    /**
     * Matches casts to a specific type.
     */
    public static PatternMatcher castTo(String typeName) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof CastInstruction)) return false;
            CastInstruction cast = (CastInstruction) instr;
            IRType targetType = cast.getTargetType();
            if (targetType instanceof ReferenceType) {
                return typeName.equals(((ReferenceType) targetType).getInternalName());
            }
            return false;
        };
    }

    // ===== Object Creation Patterns =====

    /**
     * Matches any object allocation.
     */
    public static PatternMatcher anyNew() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof NewInstruction;
    }

    /**
     * Matches allocation of a specific class.
     */
    public static PatternMatcher newInstance(String className) {
        return (instr, method, sourceMethod, classFile) -> {
            if (!(instr instanceof NewInstruction)) return false;
            NewInstruction ni = (NewInstruction) instr;
            return className.equals(ni.getClassName());
        };
    }

    /**
     * Matches any array allocation.
     */
    public static PatternMatcher anyNewArray() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof NewArrayInstruction;
    }

    // ===== Control Flow Patterns =====

    /**
     * Matches null comparisons in branches.
     */
    public static PatternMatcher nullCheck() {
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
     */
    public static PatternMatcher anyThrow() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof ThrowInstruction;
    }

    /**
     * Matches return instructions.
     */
    public static PatternMatcher anyReturn() {
        return (instr, method, sourceMethod, classFile) -> instr instanceof ReturnInstruction;
    }

    // ===== Combination Patterns =====

    /**
     * Combines patterns with AND logic.
     */
    public static PatternMatcher and(PatternMatcher... matchers) {
        return (instr, method, sourceMethod, classFile) -> {
            for (PatternMatcher matcher : matchers) {
                if (!matcher.matches(instr, method, sourceMethod, classFile)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * Combines patterns with OR logic.
     */
    public static PatternMatcher or(PatternMatcher... matchers) {
        return (instr, method, sourceMethod, classFile) -> {
            for (PatternMatcher matcher : matchers) {
                if (matcher.matches(instr, method, sourceMethod, classFile)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * Negates a pattern.
     */
    public static PatternMatcher not(PatternMatcher matcher) {
        return (instr, method, sourceMethod, classFile) ->
            !matcher.matches(instr, method, sourceMethod, classFile);
    }
}
