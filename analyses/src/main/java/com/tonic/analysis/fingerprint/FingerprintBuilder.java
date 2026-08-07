package com.tonic.analysis.fingerprint;

import com.tonic.analysis.fingerprint.features.Level0Features;
import com.tonic.analysis.fingerprint.features.Level1Features;
import com.tonic.analysis.fingerprint.features.Level2Features;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.*;
import com.tonic.util.InstructionLength;
import com.tonic.util.Opcode;

import java.util.*;

import static com.tonic.util.Opcode.*;

/**
 * Extractor that computes a MethodFingerprint from a method's descriptor and raw bytecode.
 */
public class FingerprintBuilder
{
    /**
     * Creates a stateless builder.
     */
    public FingerprintBuilder()
    {
    }

    /**
     * Builds a three-level fingerprint for a method by scanning its descriptor and bytecode.
     * @param method the method to fingerprint
     * @param classFile the owning class, used for the method id and constant-pool resolution; may be null
     * @return the computed fingerprint
     */
    public MethodFingerprint build(MethodEntry method, ClassFile classFile)
    {
        String methodId = buildMethodId(method, classFile);

        Level0Features l0 = extractLevel0(method, classFile);
        Level1Features l1 = extractLevel1(method);
        Level2Features l2 = extractLevel2(method);

        return new MethodFingerprint(methodId, l0, l1, l2);
    }

    private String buildMethodId(MethodEntry method, ClassFile classFile)
    {
        String className = classFile != null ? classFile.getClassName() : "unknown";
        return className + "." + method.getName() + method.getDesc();
    }

    private Level0Features extractLevel0(MethodEntry method, ClassFile classFile)
    {
        String desc = method.getDesc();
        String returnType = extractReturnType(desc);
        List<String> paramTypes = extractParamTypes(desc);

        CodeAttribute code = method.getCodeAttribute();
        int exceptionHandlers = 0;
        int monitorCount = 0;
        Set<String> externalCalls = new TreeSet<>();
        Set<String> fieldAccesses = new TreeSet<>();
        Set<String> instantiatedTypes = new TreeSet<>();

        if (code != null)
        {
            List<ExceptionTableEntry> exTable = code.getExceptionTable();
            if (exTable != null)
            {
                exceptionHandlers = exTable.size();
            }

            byte[] bytecode = code.getCode();
            if (bytecode != null && classFile != null)
            {
                ConstPool cp = classFile.getConstPool();
                extractLevel0FromBytecode(bytecode, cp, externalCalls, fieldAccesses, instantiatedTypes);
                monitorCount = countMonitorOps(bytecode);
            }
        }

        return new Level0Features(returnType, paramTypes.size(), paramTypes,
                exceptionHandlers, monitorCount,
                externalCalls, fieldAccesses, instantiatedTypes);
    }

    private void extractLevel0FromBytecode(byte[] bytecode, ConstPool cp, Set<String> externalCalls, Set<String> fieldAccesses, Set<String> instantiatedTypes)
    {
        int i = 0;
        while (i < bytecode.length)
        {
            int op = Byte.toUnsignedInt(bytecode[i]);
            int len = InstructionLength.at(bytecode, i);
            if (len <= 0)
            {
                i++;
                continue;
            }

            switch (Opcode.fromCode(op))
            {
                case GETSTATIC: case PUTSTATIC: case GETFIELD: case PUTFIELD:
                    if (i + 2 < bytecode.length)
                    {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String fieldRef = resolveFieldRef(cp, idx);
                        if (fieldRef != null)
                        {
                            fieldAccesses.add(fieldRef);
                        }
                    }
                    break;

                case INVOKEVIRTUAL: case INVOKESPECIAL: case INVOKESTATIC:
                    if (i + 2 < bytecode.length)
                    {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String methodRef = resolveMethodRef(cp, idx);
                        if (methodRef != null)
                        {
                            externalCalls.add(methodRef);
                        }
                    }
                    break;

                case INVOKEINTERFACE:
                    if (i + 2 < bytecode.length)
                    {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String methodRef = resolveInterfaceMethodRef(cp, idx);
                        if (methodRef != null)
                        {
                            externalCalls.add(methodRef);
                        }
                    }
                    break;

                case NEW:
                    if (i + 2 < bytecode.length)
                    {
                        int idx = readUnsignedShort(bytecode, i + 1);
                        String classRef = resolveClassRef(cp, idx);
                        if (classRef != null)
                        {
                            instantiatedTypes.add(classRef);
                        }
                    }
                    break;
            }

            i += len;
        }
    }

    private int countMonitorOps(byte[] bytecode)
    {
        int count = 0;
        for (byte b : bytecode)
        {
            int op = Byte.toUnsignedInt(b);
            if (op == MONITORENTER.getCode() || op == MONITOREXIT.getCode())
            {
                count++;
            }
        }
        return count;
    }

    private Level1Features extractLevel1(MethodEntry method)
    {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null)
        {
            return new Level1Features(0, 0, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), 0);
        }

        int loopCount = 0;
        int maxNesting = 0;
        int blockCount = 1;
        Map<String, Integer> branchTypes = new HashMap<>();
        Map<String, Integer> arithmeticOps = new HashMap<>();
        Map<String, Integer> invokeTypes = new HashMap<>();
        int arrayFlags = 0;

        byte[] bytecode = code.getCode();
        if (bytecode != null)
        {
            int i = 0;
            while (i < bytecode.length)
            {
                int op = Byte.toUnsignedInt(bytecode[i]);
                int len = InstructionLength.at(bytecode, i);
                if (len <= 0)
                {
                    i++;
                    continue;
                }

                categorizeBranchOp(op, branchTypes);
                categorizeArithmeticOp(op, arithmeticOps);
                categorizeInvokeOp(op, invokeTypes);
                arrayFlags |= getArrayFlag(op);

                if (isBranchInstruction(op))
                {
                    blockCount++;
                }

                i += len;
            }

            loopCount = estimateLoopCount(bytecode);
        }

        return new Level1Features(loopCount, maxNesting, blockCount,
                branchTypes, arithmeticOps, invokeTypes, arrayFlags);
    }

    private Level2Features extractLevel2(MethodEntry method)
    {
        CodeAttribute code = method.getCodeAttribute();
        if (code == null)
        {
            return new Level2Features(new HashMap<>(), new HashMap<>(), 0, new HashMap<>(), new HashMap<>());
        }

        Map<String, Integer> opcodeNgrams = new HashMap<>();
        Map<String, Integer> cfgEdges = new HashMap<>();
        int dominanceDepth = 1;
        Map<String, Integer> terminatorTypes = new HashMap<>();
        Map<String, Integer> instructionTypes = new HashMap<>();

        byte[] bytecode = code.getCode();
        if (bytecode != null)
        {
            String prevCategory = null;
            int i = 0;
            while (i < bytecode.length)
            {
                int op = Byte.toUnsignedInt(bytecode[i]);
                int len = InstructionLength.at(bytecode, i);
                if (len <= 0)
                {
                    i++;
                    continue;
                }

                String category = Level2Features.getOpcodeCategory(op);
                instructionTypes.merge(category, 1, Integer::sum);

                if (prevCategory != null)
                {
                    String ngram = prevCategory + "->" + category;
                    opcodeNgrams.merge(ngram, 1, Integer::sum);
                }
                prevCategory = category;

                if (isTerminator(op))
                {
                    String termType = getTerminatorType(op);
                    terminatorTypes.merge(termType, 1, Integer::sum);
                }

                i += len;
            }
        }

        return new Level2Features(opcodeNgrams, cfgEdges, dominanceDepth, terminatorTypes, instructionTypes);
    }

    private String extractReturnType(String desc)
    {
        int idx = desc.lastIndexOf(')');
        return idx >= 0 ? desc.substring(idx + 1) : "V";
    }

    private List<String> extractParamTypes(String desc)
    {
        List<String> types = new ArrayList<>();
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')')
        {
            char c = desc.charAt(i);
            if (c == 'L')
            {
                int end = desc.indexOf(';', i);
                if (end < 0) break;
                types.add(desc.substring(i, end + 1));
                i = end + 1;
            }
            else if (c == '[')
            {
                int start = i;
                while (i < desc.length() && desc.charAt(i) == '[') i++;
                if (i >= desc.length()) break;
                if (desc.charAt(i) == 'L')
                {
                    int end = desc.indexOf(';', i);
                    if (end < 0) break;
                    types.add(desc.substring(start, end + 1));
                    i = end + 1;
                }
                else
                {
                    types.add(desc.substring(start, i + 1));
                    i++;
                }
            }
            else
            {
                types.add(String.valueOf(c));
                i++;
            }
        }
        return types;
    }

    private void categorizeBranchOp(int op, Map<String, Integer> branches)
    {
        if (op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode())
        {
            branches.merge("conditional", 1, Integer::sum);
        }
        else if (op == GOTO.getCode() || op == GOTO_W.getCode())
        {
            branches.merge("goto", 1, Integer::sum);
        }
        else if (op == TABLESWITCH.getCode() || op == LOOKUPSWITCH.getCode())
        {
            branches.merge("switch", 1, Integer::sum);
        }
    }

    private void categorizeArithmeticOp(int op, Map<String, Integer> arithmetic)
    {
        if ((op >= IADD.getCode() && op <= LXOR.getCode()) || (op >= INEG.getCode() && op <= DNEG.getCode()))
        {
            arithmetic.merge("math", 1, Integer::sum);
        }
    }

    private void categorizeInvokeOp(int op, Map<String, Integer> invokes)
    {
        switch (Opcode.fromCode(op))
        {
            case INVOKEVIRTUAL: invokes.merge("virtual", 1, Integer::sum); break;
            case INVOKESPECIAL: invokes.merge("special", 1, Integer::sum); break;
            case INVOKESTATIC: invokes.merge("static", 1, Integer::sum); break;
            case INVOKEINTERFACE: invokes.merge("interface", 1, Integer::sum); break;
            case INVOKEDYNAMIC: invokes.merge("dynamic", 1, Integer::sum); break;
        }
    }

    private int getArrayFlag(int op)
    {
        if (op >= IALOAD.getCode() && op <= SALOAD.getCode()) return Level1Features.ARRAY_LOAD;
        if (op >= IASTORE.getCode() && op <= SASTORE.getCode()) return Level1Features.ARRAY_STORE;
        if (op == NEWARRAY.getCode() || op == ANEWARRAY.getCode() || op == MULTIANEWARRAY.getCode()) return Level1Features.ARRAY_NEW;
        if (op == ARRAYLENGTH.getCode()) return Level1Features.ARRAY_LENGTH;
        return 0;
    }

    private boolean isBranchInstruction(int op)
    {
        return (op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode()) || op == GOTO.getCode() || op == JSR.getCode() ||
               op == TABLESWITCH.getCode() || op == LOOKUPSWITCH.getCode() || op == GOTO_W.getCode() || op == JSR_W.getCode();
    }

    private boolean isTerminator(int op)
    {
        return (op >= IRETURN.getCode() && op <= RETURN_.getCode()) || op == ATHROW.getCode() ||
               op == GOTO.getCode() || op == GOTO_W.getCode();
    }

    private String getTerminatorType(int op)
    {
        if (op >= IRETURN.getCode() && op <= ARETURN.getCode()) return "return_value";
        if (op == RETURN_.getCode()) return "return_void";
        if (op == ATHROW.getCode()) return "athrow";
        if (op == GOTO.getCode() || op == GOTO_W.getCode()) return "goto";
        return "other";
    }

    private int estimateLoopCount(byte[] bytecode)
    {
        int backwardJumps = 0;
        int i = 0;
        while (i < bytecode.length)
        {
            int op = Byte.toUnsignedInt(bytecode[i]);
            int len = InstructionLength.at(bytecode, i);
            if (len <= 0)
            {
                i++;
                continue;
            }

            if ((op >= IFEQ.getCode() && op <= IF_ACMPNE.getCode()) || op == GOTO.getCode())
            {
                if (i + 2 < bytecode.length)
                {
                    short offset = (short) readUnsignedShort(bytecode, i + 1);
                    if (offset < 0)
                    {
                        backwardJumps++;
                    }
                }
            }
            else if (op == GOTO_W.getCode())
            {
                if (i + 4 < bytecode.length)
                {
                    int offset = readInt(bytecode, i + 1);
                    if (offset < 0)
                    {
                        backwardJumps++;
                    }
                }
            }

            i += len;
        }
        return backwardJumps;
    }

    private String resolveFieldRef(ConstPool cp, int idx)
    {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof FieldRefItem)
        {
            FieldRefItem ref = (FieldRefItem) item;
            return ref.getClassName() + "." + ref.getName();
        }
        return null;
    }

    private String resolveMethodRef(ConstPool cp, int idx)
    {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof MethodRefItem)
        {
            MethodRefItem ref = (MethodRefItem) item;
            return ref.getClassName() + "." + ref.getName() + ref.getDescriptor();
        }
        return null;
    }

    private String resolveInterfaceMethodRef(ConstPool cp, int idx)
    {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof InterfaceRefItem)
        {
            InterfaceRefItem ref = (InterfaceRefItem) item;
            return ref.getOwner() + "." + ref.getName() + ref.getDescriptor();
        }
        return null;
    }

    private String resolveClassRef(ConstPool cp, int idx)
    {
        if (cp == null) return null;
        Item<?> item = cp.getItem(idx);
        if (item instanceof ClassRefItem)
        {
            return ((ClassRefItem) item).getClassName();
        }
        return null;
    }

    private int readUnsignedShort(byte[] bytecode, int offset)
    {
        return ((bytecode[offset] & 0xFF) << 8) | (bytecode[offset + 1] & 0xFF);
    }

    private int readInt(byte[] bytecode, int offset)
    {
        return ((bytecode[offset] & 0xFF) << 24) |
               ((bytecode[offset + 1] & 0xFF) << 16) |
               ((bytecode[offset + 2] & 0xFF) << 8) |
               (bytecode[offset + 3] & 0xFF);
    }

}
