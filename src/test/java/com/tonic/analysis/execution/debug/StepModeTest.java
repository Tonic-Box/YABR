package com.tonic.analysis.execution.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepModeTest {

    @Test
    void testAllEnumValuesExist() {
        StepMode[] values = StepMode.values();
        assertEquals(5, values.length);

        assertNotNull(StepMode.RUN);
        assertNotNull(StepMode.STEP_INTO);
        assertNotNull(StepMode.STEP_OVER);
        assertNotNull(StepMode.STEP_OUT);
        assertNotNull(StepMode.RUN_TO_CURSOR);
    }

    @Test
    void testValueOfRun() {
        assertEquals(StepMode.RUN, StepMode.valueOf("RUN"));
    }

    @Test
    void testValueOfStepInto() {
        assertEquals(StepMode.STEP_INTO, StepMode.valueOf("STEP_INTO"));
    }

    @Test
    void testValueOfStepOver() {
        assertEquals(StepMode.STEP_OVER, StepMode.valueOf("STEP_OVER"));
    }

    @Test
    void testValueOfStepOut() {
        assertEquals(StepMode.STEP_OUT, StepMode.valueOf("STEP_OUT"));
    }

    @Test
    void testValueOfRunToCursor() {
        assertEquals(StepMode.RUN_TO_CURSOR, StepMode.valueOf("RUN_TO_CURSOR"));
    }

    @Test
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class, () -> StepMode.valueOf("INVALID"));
    }

    @Test
    void testOrderOfValues() {
        StepMode[] values = StepMode.values();
        assertEquals(StepMode.RUN, values[0]);
        assertEquals(StepMode.STEP_INTO, values[1]);
        assertEquals(StepMode.STEP_OVER, values[2]);
        assertEquals(StepMode.STEP_OUT, values[3]);
        assertEquals(StepMode.RUN_TO_CURSOR, values[4]);
    }
}
