package com.tonic.renamer.descriptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for DescriptorRemapper.
 * Tests remapping of class names in method descriptors, field descriptors, and type descriptors.
 */
class DescriptorRemapperTest {

    private DescriptorRemapper remapper;
    private Map<String, String> classMappings;

    @BeforeEach
    void setUp() {
        classMappings = new HashMap<>();
        classMappings.put("com/old/Type", "com/new/Type");
        classMappings.put("com/old/Result", "com/new/Result");
        classMappings.put("com/example/OldClass", "com/example/NewClass");
        classMappings.put("java/util/OldList", "java/util/NewList");

        remapper = new DescriptorRemapper(classMappings);
    }

    // ========== Constructor Tests ==========

    @Test
    @DisplayName("Constructor with Map creates remapper")
    void constructorWithMapCreatesRemapper() {
        assertNotNull(remapper);
    }

    @Test
    @DisplayName("Constructor with Function creates remapper")
    void constructorWithFunctionCreatesRemapper() {
        Function<String, String> mapper = name -> name.replace("old", "new");
        DescriptorRemapper functionRemapper = new DescriptorRemapper(mapper);
        assertNotNull(functionRemapper);
    }

    // ========== Primitive Type Tests ==========

    @Test
    @DisplayName("Primitives are not remapped - byte")
    void primitiveByteNotRemapped() {
        String descriptor = "B";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - char")
    void primitiveCharNotRemapped() {
        String descriptor = "C";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - double")
    void primitiveDoubleNotRemapped() {
        String descriptor = "D";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - float")
    void primitiveFloatNotRemapped() {
        String descriptor = "F";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - int")
    void primitiveIntNotRemapped() {
        String descriptor = "I";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - long")
    void primitiveLongNotRemapped() {
        String descriptor = "J";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - short")
    void primitiveShortNotRemapped() {
        String descriptor = "S";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - boolean")
    void primitiveBooleanNotRemapped() {
        String descriptor = "Z";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Primitives are not remapped - void")
    void primitiveVoidNotRemapped() {
        String descriptor = "V";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Reference Type Tests ==========

    @Test
    @DisplayName("Single reference type is remapped")
    void singleReferenceTypeRemapped() {
        String descriptor = "Lcom/old/Type;";
        String expected = "Lcom/new/Type;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Reference type without mapping is unchanged")
    void referenceTypeWithoutMappingUnchanged() {
        String descriptor = "Ljava/lang/String;";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Multiple different reference types are remapped")
    void multipleDifferentReferenceTypesRemapped() {
        String descriptor = "Lcom/old/Type;Lcom/old/Result;";
        String expected = "Lcom/new/Type;Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Array Type Tests ==========

    @Test
    @DisplayName("Single dimension primitive array is not remapped")
    void singleDimensionPrimitiveArrayNotRemapped() {
        String descriptor = "[I";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Multi dimension primitive array is not remapped")
    void multiDimensionPrimitiveArrayNotRemapped() {
        String descriptor = "[[I";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Single dimension reference array is remapped")
    void singleDimensionReferenceArrayRemapped() {
        String descriptor = "[Lcom/old/Type;";
        String expected = "[Lcom/new/Type;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Multi dimension reference array is remapped")
    void multiDimensionReferenceArrayRemapped() {
        String descriptor = "[[Lcom/old/Type;";
        String expected = "[[Lcom/new/Type;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Three dimension reference array is remapped")
    void threeDimensionReferenceArrayRemapped() {
        String descriptor = "[[[Lcom/old/Result;";
        String expected = "[[[Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Mixed array of unmapped reference type is unchanged")
    void mixedArrayOfUnmappedReferenceTypeUnchanged() {
        String descriptor = "[[Ljava/lang/Object;";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Method Descriptor Tests ==========

    @Test
    @DisplayName("Method descriptor with no parameters or return value")
    void methodDescriptorNoParamsNoReturn() {
        String descriptor = "()V";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with primitive parameters")
    void methodDescriptorWithPrimitiveParams() {
        String descriptor = "(IJ)V";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with single reference parameter is remapped")
    void methodDescriptorWithSingleReferenceParamRemapped() {
        String descriptor = "(Lcom/old/Type;)V";
        String expected = "(Lcom/new/Type;)V";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with multiple parameters is remapped")
    void methodDescriptorWithMultipleParamsRemapped() {
        String descriptor = "(ILcom/old/Type;J)V";
        String expected = "(ILcom/new/Type;J)V";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with reference return type is remapped")
    void methodDescriptorWithReferenceReturnRemapped() {
        String descriptor = "()Lcom/old/Result;";
        String expected = "()Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with params and return is remapped")
    void methodDescriptorWithParamsAndReturnRemapped() {
        String descriptor = "(Lcom/old/Type;I)Lcom/old/Result;";
        String expected = "(Lcom/new/Type;I)Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with array parameters is remapped")
    void methodDescriptorWithArrayParamsRemapped() {
        String descriptor = "([Lcom/old/Type;[I)V";
        String expected = "([Lcom/new/Type;[I)V";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with array return type is remapped")
    void methodDescriptorWithArrayReturnRemapped() {
        String descriptor = "(I)[Lcom/old/Result;";
        String expected = "(I)[Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Complex method descriptor is fully remapped")
    void complexMethodDescriptorFullyRemapped() {
        String descriptor = "([Lcom/old/Type;ILjava/lang/String;[[Lcom/old/Result;)Lcom/example/OldClass;";
        String expected = "([Lcom/new/Type;ILjava/lang/String;[[Lcom/new/Result;)Lcom/example/NewClass;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Method descriptor with unmapped types is unchanged")
    void methodDescriptorWithUnmappedTypesUnchanged() {
        String descriptor = "(Ljava/lang/String;I)Ljava/lang/Object;";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Field Descriptor Tests ==========

    @Test
    @DisplayName("Field descriptor for primitive type is unchanged")
    void fieldDescriptorPrimitiveUnchanged() {
        String descriptor = "I";
        assertEquals(descriptor, remapper.remapFieldDescriptor(descriptor));
    }

    @Test
    @DisplayName("Field descriptor for reference type is remapped")
    void fieldDescriptorReferenceRemapped() {
        String descriptor = "Lcom/old/Type;";
        String expected = "Lcom/new/Type;";
        assertEquals(expected, remapper.remapFieldDescriptor(descriptor));
    }

    @Test
    @DisplayName("Field descriptor for array type is remapped")
    void fieldDescriptorArrayRemapped() {
        String descriptor = "[Lcom/old/Type;";
        String expected = "[Lcom/new/Type;";
        assertEquals(expected, remapper.remapFieldDescriptor(descriptor));
    }

    @Test
    @DisplayName("Field descriptor for unmapped type is unchanged")
    void fieldDescriptorUnmappedUnchanged() {
        String descriptor = "Ljava/lang/String;";
        assertEquals(descriptor, remapper.remapFieldDescriptor(descriptor));
    }

    // ========== RemapType Tests ==========

    @Test
    @DisplayName("RemapType handles primitive")
    void remapTypeHandlesPrimitive() {
        String descriptor = "I";
        assertEquals(descriptor, remapper.remapType(descriptor));
    }

    @Test
    @DisplayName("RemapType handles reference type")
    void remapTypeHandlesReferenceType() {
        String descriptor = "Lcom/old/Type;";
        String expected = "Lcom/new/Type;";
        assertEquals(expected, remapper.remapType(descriptor));
    }

    @Test
    @DisplayName("RemapType handles array type")
    void remapTypeHandlesArrayType() {
        String descriptor = "[[Lcom/old/Result;";
        String expected = "[[Lcom/new/Result;";
        assertEquals(expected, remapper.remapType(descriptor));
    }

    // ========== RemapClassName Tests ==========

    @Test
    @DisplayName("RemapClassName remaps class name")
    void remapClassNameRemapsClassName() {
        String className = "com/old/Type";
        String expected = "com/new/Type";
        assertEquals(expected, remapper.remapClassName(className));
    }

    @Test
    @DisplayName("RemapClassName returns original for unmapped class")
    void remapClassNameReturnsOriginalForUnmapped() {
        String className = "com/unmapped/Class";
        assertEquals(className, remapper.remapClassName(className));
    }

    @Test
    @DisplayName("RemapClassName returns null for null input")
    void remapClassNameReturnsNullForNull() {
        assertNull(remapper.remapClassName(null));
    }

    // ========== NeedsRemapping Tests ==========

    @Test
    @DisplayName("NeedsRemapping returns true for remappable descriptor")
    void needsRemappingReturnsTrueForRemappable() {
        String descriptor = "(Lcom/old/Type;)V";
        assertTrue(remapper.needsRemapping(descriptor));
    }

    @Test
    @DisplayName("NeedsRemapping returns false for primitive descriptor")
    void needsRemappingReturnsFalseForPrimitive() {
        String descriptor = "(IJ)V";
        assertFalse(remapper.needsRemapping(descriptor));
    }

    @Test
    @DisplayName("NeedsRemapping returns false for unmapped reference")
    void needsRemappingReturnsFalseForUnmapped() {
        String descriptor = "(Ljava/lang/String;)V";
        assertFalse(remapper.needsRemapping(descriptor));
    }

    @Test
    @DisplayName("NeedsRemapping returns true for array of remappable type")
    void needsRemappingReturnsTrueForRemappableArray() {
        String descriptor = "[Lcom/old/Type;";
        assertTrue(remapper.needsRemapping(descriptor));
    }

    @Test
    @DisplayName("NeedsRemapping returns false for null")
    void needsRemappingReturnsFalseForNull() {
        assertFalse(remapper.needsRemapping(null));
    }

    @Test
    @DisplayName("NeedsRemapping returns false for empty string")
    void needsRemappingReturnsFalseForEmpty() {
        assertFalse(remapper.needsRemapping(""));
    }

    // ========== Edge Cases Tests ==========

    @Test
    @DisplayName("Empty descriptor returns empty")
    void emptyDescriptorReturnsEmpty() {
        assertEquals("", remapper.remapMethodDescriptor(""));
    }

    @Test
    @DisplayName("Null descriptor returns null")
    void nullDescriptorReturnsNull() {
        assertNull(remapper.remapMethodDescriptor(null));
    }

    @Test
    @DisplayName("Malformed descriptor without semicolon is handled")
    void malformedDescriptorWithoutSemicolon() {
        String descriptor = "(Lcom/old/Type)V";
        // Should copy rest of descriptor as-is when malformed
        String result = remapper.remapMethodDescriptor(descriptor);
        assertNotNull(result);
    }

    @Test
    @DisplayName("Descriptor with only opening parenthesis is handled")
    void descriptorWithOnlyOpenParen() {
        String descriptor = "(";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Multiple consecutive reference types are remapped")
    void multipleConsecutiveReferenceTypesRemapped() {
        String descriptor = "(Lcom/old/Type;Lcom/old/Result;Lcom/example/OldClass;)V";
        String expected = "(Lcom/new/Type;Lcom/new/Result;Lcom/example/NewClass;)V";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Function-based Constructor Tests ==========

    @Test
    @DisplayName("Function-based remapper with custom logic")
    void functionBasedRemapperWithCustomLogic() {
        Function<String, String> customMapper = className -> {
            if (className.startsWith("com/old/")) {
                return className.replace("com/old/", "com/new/");
            }
            return null; // No mapping
        };

        DescriptorRemapper customRemapper = new DescriptorRemapper(customMapper);
        String descriptor = "(Lcom/old/Type;)Lcom/old/Result;";
        String expected = "(Lcom/new/Type;)Lcom/new/Result;";
        assertEquals(expected, customRemapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Function-based remapper returning same value")
    void functionBasedRemapperReturningSameValue() {
        Function<String, String> identityMapper = className -> className;
        DescriptorRemapper identityRemapper = new DescriptorRemapper(identityMapper);

        String descriptor = "(Lcom/old/Type;)V";
        // When mapper returns same value, no change should occur
        assertEquals(descriptor, identityRemapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Function-based remapper with null return")
    void functionBasedRemapperWithNullReturn() {
        Function<String, String> nullMapper = className -> null;
        DescriptorRemapper nullRemapper = new DescriptorRemapper(nullMapper);

        String descriptor = "(Lcom/old/Type;)V";
        assertEquals(descriptor, nullRemapper.remapMethodDescriptor(descriptor));
    }

    // ========== Real-world Scenario Tests ==========

    @Test
    @DisplayName("Real-world: Object.equals signature")
    void realWorldObjectEqualsSignature() {
        String descriptor = "(Ljava/lang/Object;)Z";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Real-world: Map.put signature with remapping")
    void realWorldMapPutSignatureWithRemapping() {
        Map<String, String> mappings = new HashMap<>();
        mappings.put("java/lang/Object", "custom/Object");
        DescriptorRemapper customRemapper = new DescriptorRemapper(mappings);

        String descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
        String expected = "(Lcustom/Object;Lcustom/Object;)Lcustom/Object;";
        assertEquals(expected, customRemapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Real-world: Collection.toArray signature")
    void realWorldCollectionToArraySignature() {
        String descriptor = "([Ljava/lang/Object;)[Ljava/lang/Object;";
        assertEquals(descriptor, remapper.remapMethodDescriptor(descriptor));
    }

    @Test
    @DisplayName("Real-world: Generic bridge method descriptor")
    void realWorldGenericBridgeMethodDescriptor() {
        String descriptor = "(Lcom/old/Type;Ljava/util/List;)Lcom/old/Result;";
        String expected = "(Lcom/new/Type;Ljava/util/List;)Lcom/new/Result;";
        assertEquals(expected, remapper.remapMethodDescriptor(descriptor));
    }

    // ========== Performance and Consistency Tests ==========

    @Test
    @DisplayName("Remapping is idempotent")
    void remappingIsIdempotent() {
        String descriptor = "(Lcom/old/Type;)Lcom/old/Result;";
        String firstRemap = remapper.remapMethodDescriptor(descriptor);
        String secondRemap = remapper.remapMethodDescriptor(firstRemap);

        assertEquals(firstRemap, secondRemap);
    }

    @Test
    @DisplayName("Field and type remapping are consistent")
    void fieldAndTypeRemappingAreConsistent() {
        String descriptor = "Lcom/old/Type;";
        String fieldResult = remapper.remapFieldDescriptor(descriptor);
        String typeResult = remapper.remapType(descriptor);

        assertEquals(fieldResult, typeResult);
    }

    @Test
    @DisplayName("NeedsRemapping is consistent with actual remapping")
    void needsRemappingIsConsistentWithRemapping() {
        String descriptor = "(Lcom/old/Type;)V";
        boolean needsRemap = remapper.needsRemapping(descriptor);
        String remapped = remapper.remapMethodDescriptor(descriptor);

        assertTrue(needsRemap);
        assertNotEquals(descriptor, remapped);
    }

    @Test
    @DisplayName("NeedsRemapping correctly identifies no remapping needed")
    void needsRemappingCorrectlyIdentifiesNoRemapping() {
        String descriptor = "(I)V";
        boolean needsRemap = remapper.needsRemapping(descriptor);
        String remapped = remapper.remapMethodDescriptor(descriptor);

        assertFalse(needsRemap);
        assertEquals(descriptor, remapped);
    }
}
