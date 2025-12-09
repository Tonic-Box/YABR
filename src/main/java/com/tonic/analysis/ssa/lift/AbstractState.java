package com.tonic.analysis.ssa.lift;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.*;

/**
 * Abstract state for stack simulation during bytecode lifting.
 */
public class AbstractState {

    private final Deque<Value> stack;
    private final Map<Integer, Value> locals;

    public AbstractState() {
        this.stack = new ArrayDeque<>();
        this.locals = new HashMap<>();
    }

    public AbstractState(AbstractState other) {
        this.stack = new ArrayDeque<>(other.stack);
        this.locals = new HashMap<>(other.locals);
    }

    public void push(Value value) {
        stack.push(value);
    }

    // Debug context for tracking where underflow occurs
    private static String currentBlockName = "unknown";
    private static int currentInstructionOffset = -1;

    public static void setDebugContext(String blockName, int offset) {
        currentBlockName = blockName;
        currentInstructionOffset = offset;
    }

    public Value pop() {
        if (stack.isEmpty()) {
            String msg = String.format("Stack underflow at block=%s, offset=%d",
                currentBlockName, currentInstructionOffset);
            throw new IllegalStateException(msg);
        }
        return stack.pop();
    }

    public Value peek() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Stack is empty");
        }
        return stack.peek();
    }

    public Value peek(int depth) {
        Iterator<Value> it = stack.iterator();
        for (int i = 0; i < depth && it.hasNext(); i++) {
            it.next();
        }
        if (!it.hasNext()) {
            throw new IllegalStateException("Stack depth exceeded");
        }
        return it.next();
    }

    public int getStackSize() {
        return stack.size();
    }

    public boolean isStackEmpty() {
        return stack.isEmpty();
    }

    public void setLocal(int index, Value value) {
        locals.put(index, value);
    }

    public Value getLocal(int index) {
        return locals.get(index);
    }

    public boolean hasLocal(int index) {
        return locals.containsKey(index);
    }

    public Set<Integer> getLocalIndices() {
        return new HashSet<>(locals.keySet());
    }

    public void clearStack() {
        stack.clear();
    }

    public AbstractState copy() {
        return new AbstractState(this);
    }

    public void merge(AbstractState other) {
        for (Map.Entry<Integer, Value> entry : other.locals.entrySet()) {
            if (!locals.containsKey(entry.getKey())) {
                locals.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public List<Value> getStackValues() {
        return new ArrayList<>(stack);
    }

    @Override
    public String toString() {
        return "State{stack=" + stack + ", locals=" + locals + "}";
    }
}
