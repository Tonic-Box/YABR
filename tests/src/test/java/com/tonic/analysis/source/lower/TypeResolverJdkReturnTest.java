package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a JDK/library method whose declaring class is NOT loaded into the {@link ClassPool} (e.g. javax.swing
 * classes from the java.desktop module) must have its return type resolved by reflection, not defaulted to Object.
 * Pre-fix, {@code SwingUtilities.invokeLater} resolved to Object, emitting {@code invokeLater(Runnable)Object} which
 * fails with {@code NoSuchMethodError} at run time. An empty pool forces the JDK fallback path deterministically.
 */
public class TypeResolverJdkReturnTest {

    @Test
    void voidJdkMethodNotInPoolResolvesToVoid() throws Exception {
        TypeResolver resolver = new TypeResolver(new ClassPool(), "test/Owner");
        SourceType ret = resolver.resolveMethodReturnType(
            "javax/swing/SwingUtilities", "invokeLater",
            List.of(new ReferenceSourceType("java/lang/Runnable")));
        assertTrue(ret instanceof VoidSourceType, "invokeLater must resolve to void, was " + ret);
    }

    @Test
    void valueReturningJdkMethodNotInPoolResolvesByReflection() throws Exception {
        TypeResolver resolver = new TypeResolver(new ClassPool(), "test/Owner");
        SourceType ret = resolver.resolveMethodReturnType(
            "javax/swing/SwingUtilities", "isEventDispatchThread", List.of());
        assertEquals(PrimitiveSourceType.BOOLEAN, ret,
            "isEventDispatchThread must resolve to boolean, was " + ret);
    }
}
