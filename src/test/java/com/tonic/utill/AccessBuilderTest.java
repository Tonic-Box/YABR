package com.tonic.utill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AccessBuilder functionality.
 * Covers building various access flag combinations and verifying correct flag values.
 */
class AccessBuilderTest {

    private AccessBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AccessBuilder();
    }

    // ========== Single Flag Tests ==========

    @Test
    void setPublicBuildsCorrectFlag() {
        int access = builder.setPublic().build();
        assertTrue(Modifiers.isPublic(access));
        assertEquals(Modifiers.PUBLIC, access);
    }

    @Test
    void setPrivateBuildsCorrectFlag() {
        int access = builder.setPrivate().build();
        assertTrue(Modifiers.isPrivate(access));
        assertEquals(Modifiers.PRIVATE, access);
    }

    @Test
    void setProtectedBuildsCorrectFlag() {
        int access = builder.setProtected().build();
        assertTrue(Modifiers.isProtected(access));
        assertEquals(Modifiers.PROTECTED, access);
    }

    @Test
    void setStaticBuildsCorrectFlag() {
        int access = builder.setStatic().build();
        assertTrue(Modifiers.isStatic(access));
        assertEquals(Modifiers.STATIC, access);
    }

    @Test
    void setFinalBuildsCorrectFlag() {
        int access = builder.setFinal().build();
        assertTrue(Modifiers.isFinal(access));
        assertEquals(Modifiers.FINAL, access);
    }

    @Test
    void setSynchronizedBuildsCorrectFlag() {
        int access = builder.setSynchronized().build();
        assertTrue(Modifiers.isSynchronized(access));
        assertEquals(Modifiers.SYNCHRONIZED, access);
    }

    @Test
    void setBridgeBuildsCorrectFlag() {
        int access = builder.setBridge().build();
        assertTrue(Modifiers.isBridge(access));
        assertEquals(Modifiers.BRIDGE, access);
    }

    @Test
    void setVarArgsBuildsCorrectFlag() {
        int access = builder.setVarArgs().build();
        assertTrue(Modifiers.isVarArgs(access));
        assertEquals(Modifiers.VARARGS, access);
    }

    @Test
    void setNativeBuildsCorrectFlag() {
        int access = builder.setNative().build();
        assertTrue(Modifiers.isNative(access));
        assertEquals(Modifiers.NATIVE, access);
    }

    @Test
    void setAbstractBuildsCorrectFlag() {
        int access = builder.setAbstract().build();
        assertTrue(Modifiers.isAbstract(access));
        assertEquals(Modifiers.ABSTRACT, access);
    }

    @Test
    void setStrictfpBuildsCorrectFlag() {
        int access = builder.setStrictfp().build();
        assertTrue(Modifiers.isStrict(access));
        assertEquals(Modifiers.STRICT, access);
    }

    @Test
    void setSyntheticBuildsCorrectFlag() {
        int access = builder.setSynthetic().build();
        assertTrue(Modifiers.isSynthetic(access));
        assertEquals(Modifiers.SYNTHETIC, access);
    }

    @Test
    void setAnnotationBuildsCorrectFlag() {
        int access = builder.setAnnotation().build();
        assertTrue(Modifiers.isAnnotation(access));
        assertEquals(Modifiers.ANNOTATION, access);
    }

    @Test
    void setEnumBuildsCorrectFlag() {
        int access = builder.setEnum().build();
        assertTrue(Modifiers.isEnum(access));
        assertEquals(Modifiers.ENUM, access);
    }

    @Test
    void setVolatileBuildsCorrectFlag() {
        int access = builder.setVolatile().build();
        assertTrue(Modifiers.isVolatile(access));
        assertEquals(Modifiers.VOLATILE, access);
    }

    @Test
    void setTransientBuildsCorrectFlag() {
        int access = builder.setTransient().build();
        assertTrue(Modifiers.isTransient(access));
        assertEquals(Modifiers.TRANSIENT, access);
    }

    @Test
    void setInterfaceBuildsCorrectFlag() {
        int access = builder.setInterface().build();
        assertTrue(Modifiers.isInterface(access));
        assertEquals(Modifiers.INTERFACE, access);
    }

    // ========== Visibility Modifier Exclusivity Tests ==========

    @Test
    void setPublicClearsPrivate() {
        int access = builder.setPrivate().setPublic().build();
        assertTrue(Modifiers.isPublic(access));
        assertFalse(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isProtected(access));
    }

    @Test
    void setPublicClearsProtected() {
        int access = builder.setProtected().setPublic().build();
        assertTrue(Modifiers.isPublic(access));
        assertFalse(Modifiers.isProtected(access));
        assertFalse(Modifiers.isPrivate(access));
    }

    @Test
    void setPrivateClearsPublic() {
        int access = builder.setPublic().setPrivate().build();
        assertTrue(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isPublic(access));
        assertFalse(Modifiers.isProtected(access));
    }

    @Test
    void setPrivateClearsProtected() {
        int access = builder.setProtected().setPrivate().build();
        assertTrue(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isProtected(access));
        assertFalse(Modifiers.isPublic(access));
    }

    @Test
    void setProtectedClearsPublic() {
        int access = builder.setPublic().setProtected().build();
        assertTrue(Modifiers.isProtected(access));
        assertFalse(Modifiers.isPublic(access));
        assertFalse(Modifiers.isPrivate(access));
    }

    @Test
    void setProtectedClearsPrivate() {
        int access = builder.setPrivate().setProtected().build();
        assertTrue(Modifiers.isProtected(access));
        assertFalse(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isPublic(access));
    }

    // ========== Combined Flags Tests ==========

    @Test
    void combinePublicStatic() {
        int access = builder.setPublic().setStatic().build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
    }

    @Test
    void combinePublicStaticFinal() {
        int access = builder.setPublic().setStatic().setFinal().build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isFinal(access));
    }

    @Test
    void combinePrivateFinal() {
        int access = builder.setPrivate().setFinal().build();
        assertTrue(Modifiers.isPrivate(access));
        assertTrue(Modifiers.isFinal(access));
        assertFalse(Modifiers.isPublic(access));
    }

    @Test
    void combineProtectedAbstract() {
        int access = builder.setProtected().setAbstract().build();
        assertTrue(Modifiers.isProtected(access));
        assertTrue(Modifiers.isAbstract(access));
    }

    @Test
    void combinePublicSynchronized() {
        int access = builder.setPublic().setSynchronized().build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isSynchronized(access));
    }

    @Test
    void combinePublicStaticFinalSynchronized() {
        int access = builder
                .setPublic()
                .setStatic()
                .setFinal()
                .setSynchronized()
                .build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isFinal(access));
        assertTrue(Modifiers.isSynchronized(access));
    }

    @Test
    void combineFieldModifiers() {
        int access = builder
                .setPrivate()
                .setStatic()
                .setFinal()
                .setVolatile()
                .setTransient()
                .build();
        assertTrue(Modifiers.isPrivate(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isFinal(access));
        assertTrue(Modifiers.isVolatile(access));
        assertTrue(Modifiers.isTransient(access));
    }

    @Test
    void combineInterfaceAbstractPublic() {
        int access = builder
                .setPublic()
                .setInterface()
                .setAbstract()
                .build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isInterface(access));
        assertTrue(Modifiers.isAbstract(access));
    }

    @Test
    void combineEnumPublicStaticFinal() {
        int access = builder
                .setPublic()
                .setEnum()
                .setStatic()
                .setFinal()
                .build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isEnum(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isFinal(access));
    }

    @Test
    void combineBridgeSynthetic() {
        int access = builder
                .setPublic()
                .setBridge()
                .setSynthetic()
                .build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isBridge(access));
        assertTrue(Modifiers.isSynthetic(access));
    }

    // ========== Builder Chaining Tests ==========

    @Test
    void builderChainingReturnsBuilderInstance() {
        AccessBuilder result = builder.setPublic();
        assertSame(builder, result, "Builder methods should return this");
    }

    @Test
    void multipleChainedCallsWork() {
        AccessBuilder result = builder
                .setPublic()
                .setStatic()
                .setFinal()
                .setSynchronized();
        assertSame(builder, result);
    }

    // ========== Reset Tests ==========

    @Test
    void resetClearsAllFlags() {
        builder.setPublic().setStatic().setFinal().reset();
        int access = builder.build();
        assertEquals(0, access, "Reset should clear all flags");
        assertTrue(Modifiers.isPackagePrivate(access));
    }

    @Test
    void resetReturnsBuilder() {
        AccessBuilder result = builder.setPublic().reset();
        assertSame(builder, result);
    }

    @Test
    void resetAllowsReuse() {
        int firstAccess = builder.setPublic().setStatic().build();
        builder.reset();
        int secondAccess = builder.setPrivate().setFinal().build();

        assertTrue(Modifiers.isPublic(firstAccess));
        assertTrue(Modifiers.isStatic(firstAccess));

        assertTrue(Modifiers.isPrivate(secondAccess));
        assertTrue(Modifiers.isFinal(secondAccess));
        assertFalse(Modifiers.isPublic(secondAccess));
        assertFalse(Modifiers.isStatic(secondAccess));
    }

    // ========== SetAccessFlags Tests ==========

    @Test
    void setAccessFlagsSetsExactValue() {
        int expectedFlags = Modifiers.PUBLIC | Modifiers.STATIC | Modifiers.FINAL;
        int access = builder.setAccessFlags(expectedFlags).build();
        assertEquals(expectedFlags, access);
    }

    @Test
    void setAccessFlagsReplacesExistingFlags() {
        builder.setPublic().setStatic();
        int newFlags = Modifiers.PRIVATE | Modifiers.FINAL;
        int access = builder.setAccessFlags(newFlags).build();
        assertEquals(newFlags, access);
        assertTrue(Modifiers.isPrivate(access));
        assertTrue(Modifiers.isFinal(access));
        assertFalse(Modifiers.isPublic(access));
        assertFalse(Modifiers.isStatic(access));
    }

    @Test
    void setAccessFlagsReturnsBuilder() {
        AccessBuilder result = builder.setAccessFlags(Modifiers.PUBLIC);
        assertSame(builder, result);
    }

    @Test
    void setAccessFlagsCanBeChained() {
        int access = builder
                .setAccessFlags(Modifiers.PUBLIC)
                .setStatic()
                .build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
    }

    // ========== Build Multiple Times Tests ==========

    @Test
    void buildCanBeCalledMultipleTimes() {
        builder.setPublic().setStatic();
        int access1 = builder.build();
        int access2 = builder.build();
        assertEquals(access1, access2);
    }

    @Test
    void buildDoesNotClearFlags() {
        builder.setPublic().setStatic();
        builder.build();
        int access = builder.build();
        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
    }

    // ========== Empty Builder Tests ==========

    @Test
    void emptyBuilderProducesZero() {
        int access = new AccessBuilder().build();
        assertEquals(0, access);
        assertTrue(Modifiers.isPackagePrivate(access));
    }

    @Test
    void emptyBuilderHasNoFlags() {
        int access = builder.build();
        assertFalse(Modifiers.isPublic(access));
        assertFalse(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isProtected(access));
        assertFalse(Modifiers.isStatic(access));
        assertFalse(Modifiers.isFinal(access));
    }

    // ========== Complex Scenarios Tests ==========

    @Test
    void publicStaticFinalMethodFlags() {
        // Typical method: public static final
        int access = builder
                .setPublic()
                .setStatic()
                .setFinal()
                .build();

        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isFinal(access));
        assertFalse(Modifiers.isPrivate(access));
        assertFalse(Modifiers.isProtected(access));
    }

    @Test
    void privateVolatileTransientFieldFlags() {
        // Typical field: private volatile transient
        int access = builder
                .setPrivate()
                .setVolatile()
                .setTransient()
                .build();

        assertTrue(Modifiers.isPrivate(access));
        assertTrue(Modifiers.isVolatile(access));
        assertTrue(Modifiers.isTransient(access));
        assertFalse(Modifiers.isPublic(access));
    }

    @Test
    void publicAbstractInterfaceFlags() {
        // Typical interface method: public abstract
        int access = builder
                .setPublic()
                .setAbstract()
                .setInterface()
                .build();

        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isAbstract(access));
        assertTrue(Modifiers.isInterface(access));
    }

    @Test
    void syntheticBridgeMethodFlags() {
        // Compiler-generated bridge method: synthetic bridge
        int access = builder
                .setSynthetic()
                .setBridge()
                .setPublic()
                .build();

        assertTrue(Modifiers.isSynthetic(access));
        assertTrue(Modifiers.isBridge(access));
        assertTrue(Modifiers.isPublic(access));
    }

    @Test
    void nativeMethodFlags() {
        // Native method: public native
        int access = builder
                .setPublic()
                .setNative()
                .build();

        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isNative(access));
        assertFalse(Modifiers.isAbstract(access));
    }

    @Test
    void varArgsMethodFlags() {
        // Varargs method: public static varargs
        int access = builder
                .setPublic()
                .setStatic()
                .setVarArgs()
                .build();

        assertTrue(Modifiers.isPublic(access));
        assertTrue(Modifiers.isStatic(access));
        assertTrue(Modifiers.isVarArgs(access));
    }
}
