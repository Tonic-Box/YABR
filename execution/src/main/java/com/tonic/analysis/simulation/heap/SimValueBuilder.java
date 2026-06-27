package com.tonic.analysis.simulation.heap;

import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;

import java.util.*;

/**
 * Builder API for constructing simulated values with field initialization.
 * Supports building complex object graphs for simulation entry points.
 */
public final class SimValueBuilder {

    private final IRType type;
    private AllocationSite allocationSite;
    private final Map<String, Object> fieldValues;
    private final List<SimValue> arrayElements;
    private SimValue arrayLength;
    private SimValue.NullState nullState;
    private Object constantValue;

    private SimValueBuilder(IRType type) {
        this.type = type;
        this.fieldValues = new LinkedHashMap<>();
        this.arrayElements = new ArrayList<>();
        this.nullState = SimValue.NullState.MAYBE_NULL;
    }

    public static SimValueBuilder forType(IRType type) {
        return new SimValueBuilder(type);
    }

    public static SimValueBuilder forClass(String className) {
        return new SimValueBuilder(IRType.fromDescriptor("L" + className.replace('.', '/') + ";"));
    }

    public static SimValueBuilder forArray(IRType elementType) {
        return new SimValueBuilder(IRType.fromDescriptor("[" + elementType.getDescriptor()));
    }

    public SimValueBuilder withIntValue(int value) {
        this.constantValue = value;
        return this;
    }

    public SimValueBuilder withLongValue(long value) {
        this.constantValue = value;
        return this;
    }

    public SimValueBuilder withFloatValue(float value) {
        this.constantValue = value;
        return this;
    }

    public SimValueBuilder withDoubleValue(double value) {
        this.constantValue = value;
        return this;
    }

    public SimValueBuilder withBooleanValue(boolean value) {
        this.constantValue = value ? 1 : 0;
        return this;
    }

    public SimValueBuilder withStringValue(String value) {
        this.constantValue = value;
        return this;
    }

    public SimValueBuilder withAllocationSite(AllocationSite site) {
        this.allocationSite = site;
        this.nullState = SimValue.NullState.DEFINITELY_NOT_NULL;
        return this;
    }

    public SimValueBuilder withField(String fieldName, SimValue value) {
        fieldValues.put(fieldName, value);
        return this;
    }

    public SimValueBuilder withField(String fieldName, SimValueBuilder nested) {
        fieldValues.put(fieldName, nested);
        return this;
    }

    public SimValueBuilder withField(String fieldName, int value) {
        fieldValues.put(fieldName, SimValueBuilder.forType(PrimitiveType.INT).withIntValue(value));
        return this;
    }

    public SimValueBuilder withField(String fieldName, long value) {
        fieldValues.put(fieldName, SimValueBuilder.forType(PrimitiveType.LONG).withLongValue(value));
        return this;
    }

    public SimValueBuilder withField(String fieldName, boolean value) {
        fieldValues.put(fieldName, SimValueBuilder.forType(PrimitiveType.BOOLEAN).withBooleanValue(value));
        return this;
    }

    public SimValueBuilder withField(String fieldName, String value) {
        fieldValues.put(fieldName, SimValueBuilder.forClass("java/lang/String").withStringValue(value));
        return this;
    }

    public SimValueBuilder asArray(SimValue... elements) {
        this.arrayElements.clear();
        this.arrayElements.addAll(Arrays.asList(elements));
        this.arrayLength = SimValue.constant(elements.length, PrimitiveType.INT, null);
        return this;
    }

    public SimValueBuilder withArrayLength(int length) {
        this.arrayLength = SimValue.constant(length, PrimitiveType.INT, null);
        return this;
    }

    public SimValueBuilder withArrayElement(SimValue element) {
        this.arrayElements.add(element);
        return this;
    }

    public SimValueBuilder nullable() {
        this.nullState = SimValue.NullState.MAYBE_NULL;
        return this;
    }

    public SimValueBuilder definitelyNull() {
        this.nullState = SimValue.NullState.DEFINITELY_NULL;
        this.allocationSite = null;
        return this;
    }

    public SimValueBuilder definitelyNotNull() {
        this.nullState = SimValue.NullState.DEFINITELY_NOT_NULL;
        return this;
    }

    public IRType getType() {
        return type;
    }

    public SimValue build(SimHeap heap) {
        if (constantValue != null && type != null && type.isPrimitive()) {
            return SimValue.constant(constantValue, type, null);
        }

        if (nullState == SimValue.NullState.DEFINITELY_NULL) {
            return SimValue.ofNull(type, null);
        }

        if (type != null && type.isArray()) {
            return buildArray(heap);
        }

        if (type != null && type.isReference()) {
            return buildObject(heap);
        }

        if (constantValue != null) {
            return SimValue.constant(constantValue, type, null);
        }

        return SimValue.ofType(type, null);
    }

    private SimValue buildObject(SimHeap heap) {
        AllocationSite site = allocationSite;
        if (site == null) {
            site = AllocationSite.synthetic(type.getDescriptor(), "builder");
        }

        heap.allocate(site);

        if (!fieldValues.isEmpty()) {
            String ownerClass = type.getDescriptor();
            if (ownerClass.startsWith("L") && ownerClass.endsWith(";")) {
                ownerClass = ownerClass.substring(1, ownerClass.length() - 1);
            }

            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                SimValue fieldValue;
                if (value instanceof SimValue) {
                    fieldValue = (SimValue) value;
                } else if (value instanceof SimValueBuilder) {
                    fieldValue = ((SimValueBuilder) value).build(heap);
                } else {
                    continue;
                }

                String fieldDesc = inferDescriptor(fieldValue);
                FieldKey fieldKey = FieldKey.of(ownerClass, fieldName, fieldDesc);
                heap.putField(site, fieldKey, fieldValue);
            }
        }

        return SimValue.ofAllocation(site, type, null);
    }

    private SimValue buildArray(SimHeap heap) {
        AllocationSite site = allocationSite;
        if (site == null) {
            site = AllocationSite.synthetic(type.getDescriptor(), "builder-array");
        }

        IRType elementType = extractArrayElementType(type);
        SimValue length = arrayLength;
        if (length == null) {
            length = SimValue.constant(arrayElements.size(), PrimitiveType.INT, null);
        }

        heap.allocateArray(site, elementType, length);

        for (int i = 0; i < arrayElements.size(); i++) {
            SimValue indexValue = SimValue.constant(i, PrimitiveType.INT, null);
            heap.arrayStore(site, indexValue, arrayElements.get(i));
        }

        return SimValue.ofAllocation(site, type, null);
    }

    private static String inferDescriptor(SimValue value) {
        if (value == null) return "Ljava/lang/Object;";
        IRType type = value.getType();
        if (type == null) return "Ljava/lang/Object;";
        return type.getDescriptor();
    }

    private static IRType extractArrayElementType(IRType arrayType) {
        if (arrayType == null) return null;
        String desc = arrayType.getDescriptor();
        if (desc.startsWith("[")) {
            return IRType.fromDescriptor(desc.substring(1));
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SimValueBuilder[");
        sb.append("type=").append(type);
        if (constantValue != null) {
            sb.append(", const=").append(constantValue);
        }
        if (!fieldValues.isEmpty()) {
            sb.append(", fields=").append(fieldValues.size());
        }
        if (!arrayElements.isEmpty()) {
            sb.append(", elements=").append(arrayElements.size());
        }
        sb.append(", null=").append(nullState);
        sb.append("]");
        return sb.toString();
    }
}
