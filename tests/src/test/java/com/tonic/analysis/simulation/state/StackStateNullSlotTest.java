package com.tonic.analysis.simulation.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A simulated stack slot may hold null for a value the simulator has no information about, so the
 * backing copy must tolerate null elements. Building it with a null-hostile factory such as
 * {@code List.copyOf} throws a NullPointerException from inside push, which is why this is pinned.
 * Reading such a slot back is a separate question - {@code peekValue} does dereference the top slot.
 */
class StackStateNullSlotTest
{

    @Test
    void aSlotWithNoKnownValueCanBePushed()
    {
        StackState stack = StackState.empty().push(null).push(null);

        assertEquals(2, stack.depth(), "two pushes make a stack of depth two, whatever they carry");
    }

    @Test
    void aNullSlotSurvivesFurtherPushes()
    {
        StackState stack = StackState.empty().push(null);

        assertEquals(1, stack.depth());
        assertEquals(2, stack.push(null).depth(),
            "each copy of the stack must carry the null slots already on it");
    }
}
