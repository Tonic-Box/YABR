package com.tonic.analysis.frame;

import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.stack.VerificationTypeInfo;
import com.tonic.utill.Modifiers;
import lombok.Getter;

import java.util.*;
import java.util.function.Function;

/**
 * Immutable representation of the type state at a specific point in bytecode execution.
 * Contains the types of all local variables and the operand stack.
 */
@Getter
public final class TypeState {
    private static Function<String, String> superclassResolver;

    public static void setSuperclassResolver(Function<String, String> resolver) {
        superclassResolver = resolver;
    }

    private final List<VerificationType> locals;
    private final List<VerificationType> stack;

    /**
     * Constructs a TypeState with the given locals and stack.
     *
     * @param locals local variable types
     * @param stack operand stack types
     */
    public TypeState(List<VerificationType> locals, List<VerificationType> stack) {
        this.locals = Collections.unmodifiableList(new ArrayList<>(locals));
        this.stack = Collections.unmodifiableList(new ArrayList<>(stack));
    }

    /**
     * Creates an empty TypeState with no locals and empty stack.
     *
     * @return empty TypeState
     */
    public static TypeState empty() {
        return new TypeState(List.of(), List.of());
    }

    /**
     * Creates the initial TypeState from a method descriptor and access flags.
     *
     * @param method the method entry
     * @param constPool the constant pool for resolving class references
     * @return initial TypeState at method entry
     */
    public static TypeState fromMethodEntry(MethodEntry method, ConstPool constPool) {
        List<VerificationType> locals = new ArrayList<>();
        String descriptor = method.getDesc();
        boolean isStatic = Modifiers.isStatic(method.getAccess());

        if (!isStatic) {
            String ownerClass = method.getOwnerName();
            int classIndex = constPool.findOrAddClass(ownerClass).getIndex(constPool);

            if ("<init>".equals(method.getName())) {
                locals.add(VerificationType.UNINITIALIZED_THIS);
            } else {
                locals.add(VerificationType.object(classIndex));
            }
        }

        parseMethodParameters(descriptor, locals, constPool);

        return new TypeState(locals, List.of());
    }

    /**
     * Parses the method descriptor to extract parameter types.
     *
     * @param descriptor the method descriptor
     * @param locals list to populate with parameter types
     * @param constPool the constant pool
     */
    private static void parseMethodParameters(String descriptor, List<VerificationType> locals, ConstPool constPool) {
        if (!descriptor.startsWith("(")) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }

        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'B':
                case 'C':
                case 'I':
                case 'S':
                case 'Z':
                    locals.add(VerificationType.INTEGER);
                    i++;
                    break;
                case 'F':
                    locals.add(VerificationType.FLOAT);
                    i++;
                    break;
                case 'D':
                    locals.add(VerificationType.DOUBLE);
                    locals.add(VerificationType.TOP);
                    i++;
                    break;
                case 'J':
                    locals.add(VerificationType.LONG);
                    locals.add(VerificationType.TOP);
                    i++;
                    break;
                case 'L':
                    int endIndex = descriptor.indexOf(';', i);
                    if (endIndex == -1) {
                        throw new IllegalArgumentException("Invalid object type in descriptor: " + descriptor);
                    }
                    String className = descriptor.substring(i + 1, endIndex);
                    int classIndex = constPool.findOrAddClass(className).getIndex(constPool);
                    locals.add(VerificationType.object(classIndex));
                    i = endIndex + 1;
                    break;
                case '[':
                    int arrayStart = i;
                    while (i < descriptor.length() && descriptor.charAt(i) == '[') {
                        i++;
                    }
                    if (i >= descriptor.length()) {
                        throw new IllegalArgumentException("Invalid array type in descriptor: " + descriptor);
                    }
                    char elementType = descriptor.charAt(i);
                    if (elementType == 'L') {
                        int endIndex2 = descriptor.indexOf(';', i);
                        if (endIndex2 == -1) {
                            throw new IllegalArgumentException("Invalid array element type: " + descriptor);
                        }
                        String arrayDescriptor = descriptor.substring(arrayStart, endIndex2 + 1);
                        int classIndex2 = constPool.findOrAddClass(arrayDescriptor).getIndex(constPool);
                        locals.add(VerificationType.object(classIndex2));
                        i = endIndex2 + 1;
                    } else {
                        String arrayDescriptor = descriptor.substring(arrayStart, i + 1);
                        int classIndex3 = constPool.findOrAddClass(arrayDescriptor).getIndex(constPool);
                        locals.add(VerificationType.object(classIndex3));
                        i++;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unknown type in descriptor: " + c + " at " + i);
            }
        }
    }

    /**
     * Returns the return type from a method descriptor.
     *
     * @param descriptor the method descriptor
     * @param constPool the constant pool
     * @return return type or null for void
     */
    public static VerificationType getReturnType(String descriptor, ConstPool constPool) {
        int returnStart = descriptor.indexOf(')') + 1;
        if (returnStart <= 0 || returnStart >= descriptor.length()) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }

        String returnDesc = descriptor.substring(returnStart);
        if (returnDesc.equals("V")) {
            return null;
        }

        char c = returnDesc.charAt(0);
        switch (c) {
            case 'B':
            case 'C':
            case 'I':
            case 'S':
            case 'Z':
                return VerificationType.INTEGER;
            case 'F':
                return VerificationType.FLOAT;
            case 'D':
                return VerificationType.DOUBLE;
            case 'J':
                return VerificationType.LONG;
            case 'L':
                int endIndex = returnDesc.indexOf(';');
                String className = returnDesc.substring(1, endIndex);
                int classIndex = constPool.findOrAddClass(className).getIndex(constPool);
                return VerificationType.object(classIndex);
            case '[':
                int classIndex2 = constPool.findOrAddClass(returnDesc).getIndex(constPool);
                return VerificationType.object(classIndex2);
            default:
                throw new IllegalArgumentException("Unknown return type: " + returnDesc);
        }
    }

    /**
     * The operand-stack depth in slots (category-2 {@code long}/{@code double} count as 2, since they
     * are stored with a {@code TOP} filler) — i.e. the {@code max_stack} contribution of this state.
     *
     * @return the stack depth in slots
     */
    public int stackSlots() {
        return stack.size();
    }

    /**
     * Pushes a type onto the stack.
     *
     * @param type the type to push
     * @return new state with type pushed
     */
    public TypeState push(VerificationType type) {
        List<VerificationType> newStack = new ArrayList<>(stack);
        newStack.add(type);
        if (type.isTwoSlot()) {
            newStack.add(VerificationType.TOP);
        }
        return new TypeState(locals, newStack);
    }

    /**
     * Pops a single type from the stack.
     *
     * @return new state with top type removed
     */
    public TypeState pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Stack underflow");
        }
        List<VerificationType> newStack = new ArrayList<>(stack);
        newStack.remove(newStack.size() - 1);
        return new TypeState(locals, newStack);
    }

    /**
     * Pops n types from the stack.
     *
     * @param n number of types to pop
     * @return new state with n types removed
     */
    public TypeState pop(int n) {
        if (n > stack.size()) {
            throw new IllegalStateException("Stack underflow: trying to pop " + n + " but stack has " + stack.size());
        }
        List<VerificationType> newStack = new ArrayList<>(stack.subList(0, stack.size() - n));
        return new TypeState(locals, newStack);
    }

    /**
     * Gets the type at the top of the stack without popping.
     *
     * @return top stack type
     */
    public VerificationType peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Stack is empty");
        }
        return stack.get(stack.size() - 1);
    }

    /**
     * Gets the type at position from top.
     *
     * @param fromTop offset from top (0 = top)
     * @return type at position
     */
    public VerificationType peek(int fromTop) {
        int index = stack.size() - 1 - fromTop;
        if (index < 0) {
            throw new IllegalStateException("Stack underflow");
        }
        return stack.get(index);
    }

    /**
     * Clears the stack.
     *
     * @return new state with empty stack
     */
    public TypeState clearStack() {
        return new TypeState(locals, List.of());
    }

    public TypeState replaceType(VerificationType oldType, VerificationType newType) {
        List<VerificationType> newLocals = new ArrayList<>(locals.size());
        for (VerificationType local : locals) {
            newLocals.add(local.equals(oldType) ? newType : local);
        }
        List<VerificationType> newStack = new ArrayList<>(stack.size());
        for (VerificationType stackEntry : stack) {
            newStack.add(stackEntry.equals(oldType) ? newType : stackEntry);
        }
        return new TypeState(newLocals, newStack);
    }

    /**
     * Sets a local variable at the given index.
     *
     * @param index the local variable index
     * @param type the type to set
     * @return new state with local updated
     */
    public TypeState setLocal(int index, VerificationType type) {
        List<VerificationType> newLocals = new ArrayList<>(locals);

        while (newLocals.size() <= index) {
            newLocals.add(VerificationType.TOP);
        }

        newLocals.set(index, type);

        if (type.isTwoSlot() && index + 1 < newLocals.size()) {
            newLocals.set(index + 1, VerificationType.TOP);
        } else if (type.isTwoSlot()) {
            newLocals.add(VerificationType.TOP);
        }

        return new TypeState(newLocals, stack);
    }

    /**
     * Gets the local variable type at the given index.
     *
     * @param index the local variable index
     * @return type at index or TOP if out of bounds
     */
    public VerificationType getLocal(int index) {
        if (index >= locals.size()) {
            return VerificationType.TOP;
        }
        return locals.get(index);
    }

    /**
     * Gets the number of local variable slots used.
     *
     * @return locals count
     */
    public int getLocalsCount() {
        return locals.size();
    }

    /**
     * Gets the current stack depth.
     *
     * @return stack size
     */
    public int getStackSize() {
        return stack.size();
    }

    /**
     * Returns true if the stack is empty.
     *
     * @return true if stack is empty
     */
    public boolean isStackEmpty() {
        return stack.isEmpty();
    }

    /**
     * Converts locals to VerificationTypeInfo list for writing to class file.
     *
     * @return list of VerificationTypeInfo for locals
     */
    public List<VerificationTypeInfo> localsToVerificationTypeInfo() {
        return stripTwoSlotCompanions(locals);
    }

    /**
     * Converts stack to VerificationTypeInfo list for writing to class file.
     *
     * @return list of VerificationTypeInfo for stack
     */
    public List<VerificationTypeInfo> stackToVerificationTypeInfo() {
        return stripTwoSlotCompanions(stack);
    }

    private static List<VerificationTypeInfo> stripTwoSlotCompanions(List<VerificationType> types) {
        List<VerificationTypeInfo> result = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            VerificationType type = types.get(i);
            result.add(type.toVerificationTypeInfo());
            if (type.isTwoSlot() && i + 1 < types.size()
                    && types.get(i + 1).equals(VerificationType.TOP)) {
                i++;
            }
        }
        return result;
    }

    /**
     * Creates a copy of this state with the given stack replaced.
     *
     * @param newStack the new stack
     * @return new state with replaced stack
     */
    public TypeState withStack(List<VerificationType> newStack) {
        return new TypeState(locals, newStack);
    }

    /**
     * Creates a copy of this state with the given locals replaced.
     *
     * @param newLocals the new locals
     * @return new state with replaced locals
     */
    public TypeState withLocals(List<VerificationType> newLocals) {
        return new TypeState(newLocals, stack);
    }

    public TypeState merge(TypeState other) {
        return merge(other, null);
    }

    public TypeState merge(TypeState other, ConstPool constPool) {
        int maxLocals = Math.max(locals.size(), other.locals.size());
        List<VerificationType> mergedLocals = new ArrayList<>(maxLocals);

        for (int i = 0; i < maxLocals; i++) {
            VerificationType a = (i < locals.size()) ? locals.get(i) : VerificationType.TOP;
            VerificationType b = (i < other.locals.size()) ? other.locals.get(i) : VerificationType.TOP;
            mergedLocals.add(mergeTypes(a, b, constPool));
        }

        List<VerificationType> mergedStack;
        if (stack.equals(other.stack)) {
            mergedStack = stack;
        } else if (stack.size() == other.stack.size()) {
            mergedStack = new ArrayList<>(stack.size());
            for (int i = 0; i < stack.size(); i++) {
                mergedStack.add(mergeTypes(stack.get(i), other.stack.get(i), constPool));
            }
        } else if (stack.isEmpty()) {
            mergedStack = new ArrayList<>(other.stack);
        } else if (other.stack.isEmpty()) {
            mergedStack = new ArrayList<>(stack);
        } else {
            int minSize = Math.min(stack.size(), other.stack.size());
            mergedStack = new ArrayList<>(minSize);
            for (int i = 0; i < minSize; i++) {
                mergedStack.add(mergeTypes(stack.get(i), other.stack.get(i), constPool));
            }
        }

        return new TypeState(mergedLocals, mergedStack);
    }

    private static VerificationType mergeTypes(VerificationType a, VerificationType b, ConstPool constPool) {
        if (a.equals(b)) {
            return a;
        }
        boolean aIsRef = isReferenceType(a);
        boolean bIsRef = isReferenceType(b);
        if (aIsRef && b.equals(VerificationType.NULL)) return a;
        if (bIsRef && a.equals(VerificationType.NULL)) return b;
        if (aIsRef && bIsRef && constPool != null) {
            String aName = resolveTypeName(a, constPool);
            String bName = resolveTypeName(b, constPool);
            if (aName != null && bName != null && superclassResolver != null) {
                String common = findCommonSuperclass(aName, bName);
                int classIndex = constPool.findOrAddClass(common).getIndex(constPool);
                return VerificationType.object(classIndex);
            }
            int objectIndex = constPool.findOrAddClass("java/lang/Object").getIndex(constPool);
            return VerificationType.object(objectIndex);
        }
        return VerificationType.TOP;
    }

    private static String resolveTypeName(VerificationType type, ConstPool constPool) {
        if (type.getTag() == VerificationType.TAG_OBJECT) {
            VerificationType.ObjectType obj = (VerificationType.ObjectType) type;
            return constPool.getClassName(obj.getClassIndex());
        }
        return null;
    }

    private static String findCommonSuperclass(String a, String b) {
        if (a.equals(b)) return a;
        if (superclassResolver == null) return "java/lang/Object";

        Set<String> aAncestors = new LinkedHashSet<>();
        String current = a;
        while (current != null && !current.isEmpty() && aAncestors.add(current)) {
            current = superclassResolver.apply(current);
        }

        current = b;
        while (current != null && !current.isEmpty()) {
            if (aAncestors.contains(current)) {
                return current;
            }
            current = superclassResolver.apply(current);
        }

        return "java/lang/Object";
    }

    private static boolean isReferenceType(VerificationType type) {
        return type.getTag() == VerificationType.TAG_OBJECT
                || type.getTag() == VerificationType.TAG_NULL
                || type.getTag() == VerificationType.TAG_UNINITIALIZED
                || type.getTag() == VerificationType.TAG_UNINITIALIZED_THIS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeState)) return false;
        TypeState that = (TypeState) o;
        return Objects.equals(locals, that.locals) && Objects.equals(stack, that.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locals, stack);
    }

    @Override
    public String toString() {
        return "TypeState{locals=" + locals + ", stack=" + stack + "}";
    }
}
