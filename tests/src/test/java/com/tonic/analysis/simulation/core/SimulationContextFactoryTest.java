package com.tonic.analysis.simulation.core;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The forMethod and forClass factories exist to build a context around a specific class, so each must
 * resolve through the pool that class came from rather than the process-wide default.
 */
class SimulationContextFactoryTest
{

    private static ClassFile classInOwnPool(String name) throws Exception
    {
        ClassPool pool = TestUtils.emptyPool();
        return pool.createNewClass(name, new AccessBuilder().setPublic().build());
    }

    @Test
    void forClassResolvesThroughTheClassOwnPool() throws Exception
    {
        ClassFile cf = classInOwnPool("probe/Owned");
        ClassPool owning = cf.getClassPool();
        assertNotNull(owning, "the fixture needs a class that knows its pool");
        assertNotSame(ClassPool.getDefault(), owning, "the fixture pool must not be the default one");

        assertSame(owning, SimulationContext.forClass(cf).getClassPool(),
            "forClass must resolve through the pool its argument came from");
    }

    @Test
    void forMethodResolvesThroughItsDeclaringClassPool() throws Exception
    {
        ClassFile cf = classInOwnPool("probe/OwnedMethod");
        ClassPool owning = cf.getClassPool();
        MethodEntry method = cf.createNewMethod(
            new AccessBuilder().setPublic().setStatic().build(), "probe", "()V");

        assertSame(owning, SimulationContext.forMethod(method).getClassPool(),
            "forMethod must resolve through the pool its declaring class came from");
    }

    @Test
    void twoClassesFromDifferentPoolsDoNotShareAContext() throws Exception
    {
        ClassFile first = classInOwnPool("probe/First");
        ClassFile second = classInOwnPool("probe/Second");

        assertNotSame(first.getClassPool(), second.getClassPool(), "the fixture needs two distinct pools");
        assertNotSame(SimulationContext.forClass(first).getClassPool(),
            SimulationContext.forClass(second).getClassPool(),
            "a context built for one class must not resolve through another class's pool");
    }

    @Test
    void aMissingArgumentFallsBackToTheDefaultPool()
    {
        assertSame(ClassPool.getDefault(), SimulationContext.forClass(null).getClassPool(),
            "no class means no pool to take, so the default stands in");
        assertSame(ClassPool.getDefault(), SimulationContext.forMethod(null).getClassPool(),
            "no method means no pool to take, so the default stands in");
    }
}
