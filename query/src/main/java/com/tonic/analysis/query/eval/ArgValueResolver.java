package com.tonic.analysis.query.eval;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.analysis.query.ast.ArgumentType;
import com.tonic.analysis.query.util.ArgumentTypeAnalyzer;
import com.tonic.analysis.query.value.Value;

/**
 * Resolves an argument of a call to its declared type, kind and (when a constant feeds the operand)
 * literal value. The producer instruction is located by reusing {@link ArgumentTypeAnalyzer}'s cheap
 * backward stack walk; the value is read from the producer's operand. Unknown/branchy producers yield
 * {@link Value#ABSENT} for value (sound for matching, incomplete across branches).
 */
public final class ArgValueResolver
{

    private ArgValueResolver()
    {
    }

    /**
     * Reads the declared parameter descriptor out of the call's method descriptor.
     *
     * @param call the call being inspected
     * @param argIndex the zero-based parameter position
     * @return a type value for that parameter, or {@link Value#ABSENT} when the descriptor
     *         is unparsable or the position is out of range
     */
    public static Value declaredType(Subject.CallSubject call, int argIndex)
    {
        String desc = ArgumentTypeAnalyzer.getDescriptor(call.invoke());
        String param = paramDescriptor(desc, argIndex);
        return param == null ? Value.ABSENT : Value.ofType(param);
    }

    /**
     * Classifies the instruction that pushes the argument ({@code literal}/{@code local}/{@code field}/...).
     *
     * @param call the call being inspected
     * @param argIndex the zero-based parameter position
     * @return the lower-cased kind name, {@code any} when no producer could be located
     */
    public static Value kind(Subject.CallSubject call, int argIndex)
    {
        Instruction producer = producer(call, argIndex);
        ArgumentType type = producer == null ? ArgumentType.ANY : ArgumentTypeAnalyzer.classifyInstruction(producer);
        return Value.of(type.name().toLowerCase());
    }

    /**
     * Reads the constant feeding the argument.
     *
     * @param call the call being inspected
     * @param argIndex the zero-based parameter position
     * @return the constant, or {@link Value#ABSENT} when the producer is unknown or not a constant
     */
    public static Value value(Subject.CallSubject call, int argIndex)
    {
        Instruction producer = producer(call, argIndex);
        return producer == null ? Value.ABSENT : constantOf(producer);
    }

    private static Instruction producer(Subject.CallSubject call, int argIndex)
    {
        return call.context().memo("argproducer:" + call.index() + ":" + argIndex, () -> {
            CodeWriter cw = call.context().codeWriter();
            if (cw == null)
            {
                return null;
            }
            int total = ArgumentTypeAnalyzer.getArgumentCount(call.invoke());
            if (argIndex < 0 || argIndex >= total)
            {
                return null;
            }
            return ArgumentTypeAnalyzer.findArgumentProducer(cw, call.index(), argIndex, total, call.invoke());
        });
    }

    private static Value constantOf(Instruction instr)
    {
        if (instr instanceof BipushInstruction)
        {
            return Value.of(((BipushInstruction) instr).getValue());
        }
        if (instr instanceof SipushInstruction)
        {
            return Value.of(((SipushInstruction) instr).getValue());
        }
        if (instr instanceof IConstInstruction)
        {
            return Value.of(((IConstInstruction) instr).getValue());
        }
        if (instr instanceof LConstInstruction)
        {
            return Value.of(((LConstInstruction) instr).getValue());
        }
        if (instr instanceof FConstInstruction)
        {
            return Value.of(((FConstInstruction) instr).getValue());
        }
        if (instr instanceof DConstInstruction)
        {
            return Value.of(((DConstInstruction) instr).getValue());
        }
        if (instr instanceof AConstNullInstruction)
        {
            return Value.ofNull();
        }
        if (instr instanceof LdcInstruction)
        {
            return ldcValue((LdcInstruction) instr);
        }
        return Value.ABSENT;
    }

    private static Value ldcValue(LdcInstruction ldc)
    {
        String resolved = ldc.resolveConstant();
        if (resolved == null)
        {
            return Value.ABSENT;
        }
        switch (ldc.getConstantType())
        {
            case INTEGER:
                try { return Value.of(Long.parseLong(resolved.trim())); } catch (NumberFormatException e) { return Value.ABSENT; }
            case LONG:
                try { return Value.of(Long.parseLong(resolved.replace("L", "").trim())); } catch (NumberFormatException e) { return Value.ABSENT; }
            case FLOAT:
            case DOUBLE:
                try { return Value.of(Double.parseDouble(resolved.trim())); } catch (NumberFormatException e) { return Value.ABSENT; }
            case STRING:
                return Value.of(stripQuotes(resolved));
            default:
                return Value.ABSENT;
        }
    }

    private static String stripQuotes(String s)
    {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
        {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * Extracts the descriptor of parameter {@code argIndex} from a method descriptor.
     */
    private static String paramDescriptor(String desc, int argIndex)
    {
        if (desc == null || !desc.startsWith("("))
        {
            return null;
        }
        int count = 0;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')')
        {
            int start = i;
            char c = desc.charAt(i);
            if (c == '[')
            {
                while (i < desc.length() && desc.charAt(i) == '[')
                {
                    i++;
                }
                c = desc.charAt(i);
            }
            if (c == 'L')
            {
                int end = desc.indexOf(';', i);
                if (end < 0)
                {
                    return null;
                }
                i = end + 1;
            }
            else
            {
                i++;
            }
            if (count == argIndex)
            {
                return desc.substring(start, i);
            }
            count++;
        }
        return null;
    }
}
