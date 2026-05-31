package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.type.ArrayType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.type.VoidType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IrTypeMapperTest {

    @Test
    void mapsPrimitivesWithIntegralCollapse() {
        assertEquals(LlvmType.I32, IrTypeMapper.map(PrimitiveType.INT));
        assertEquals(LlvmType.I32, IrTypeMapper.map(PrimitiveType.BOOLEAN));
        assertEquals(LlvmType.I32, IrTypeMapper.map(PrimitiveType.BYTE));
        assertEquals(LlvmType.I32, IrTypeMapper.map(PrimitiveType.CHAR));
        assertEquals(LlvmType.I32, IrTypeMapper.map(PrimitiveType.SHORT));
        assertEquals(LlvmType.I64, IrTypeMapper.map(PrimitiveType.LONG));
        assertEquals(LlvmType.FLOAT, IrTypeMapper.map(PrimitiveType.FLOAT));
        assertEquals(LlvmType.DOUBLE, IrTypeMapper.map(PrimitiveType.DOUBLE));
        assertEquals(LlvmType.VOID, IrTypeMapper.map(VoidType.INSTANCE));
    }

    @Test
    void mapsDescriptorParamsAndReturn() {
        assertEquals(Arrays.asList(LlvmType.I32, LlvmType.I64, LlvmType.DOUBLE, LlvmType.FLOAT),
            IrTypeMapper.mapParams("(IJDF)V"));
        assertEquals(LlvmType.VOID, IrTypeMapper.mapReturn("(IJDF)V"));
        assertEquals(LlvmType.I32, IrTypeMapper.mapReturn("(II)I"));
        assertEquals(LlvmType.I64, IrTypeMapper.mapReturn("()J"));
    }

    @Test
    void mapsReferencesAndArraysToPtr() {
        assertEquals(LlvmType.PTR, IrTypeMapper.map(ReferenceType.OBJECT));
        assertEquals(LlvmType.PTR, IrTypeMapper.map(ArrayType.fromDescriptor("[I")));
        assertEquals(List.of(LlvmType.PTR), IrTypeMapper.mapParams("(Ljava/lang/String;)V"));
        assertEquals(LlvmType.PTR, IrTypeMapper.mapReturn("()Ljava/lang/Object;"));
        assertEquals(Arrays.asList(LlvmType.I32, LlvmType.PTR, LlvmType.PTR),
            IrTypeMapper.mapParams("(I[ILjava/lang/String;)V"));
        assertEquals(LlvmType.PTR, IrTypeMapper.mapReturn("()[Ljava/lang/String;"));
    }
}
