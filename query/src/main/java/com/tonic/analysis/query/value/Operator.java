package com.tonic.analysis.query.value;

import java.util.regex.Pattern;

/**
 * The comparison and relation vocabulary of the query DSL.
 */
public enum Operator
{
    /**
     * Equality within a value kind - type names compared in canonical form, numbers compared
     * as doubles across int and real, and two nulls equal.
     */
    EQ,
    /**
     * Negation of {@link #EQ}, so two values of incomparable kinds are unequal.
     */
    NEQ,
    /**
     * Strictly less than, numeric operands only; false for any other kind pairing.
     */
    LT,
    /**
     * Less than or equal, numeric operands only; false for any other kind pairing.
     */
    LTE,
    /**
     * Strictly greater than, numeric operands only; false for any other kind pairing.
     */
    GT,
    /**
     * Greater than or equal, numeric operands only; false for any other kind pairing.
     */
    GTE,
    /**
     * Regular-expression match of the left string against the right pattern.
     */
    MATCHES,
    /**
     * Substring or membership containment, depending on the operand kinds.
     */
    CONTAINS,
    /**
     * String prefix test; false unless both operands are strings.
     */
    STARTS_WITH,
    /**
     * String suffix test; false unless both operands are strings.
     */
    ENDS_WITH,
    /**
     * Set membership; false unless the right operand is a set.
     */
    IN,
    /**
     * Data-flow reachability from the left operand to the right, evaluated over SSA rather than
     * by {@link #test}, which always returns false for it.
     */
    FLOWS_TO,
    /**
     * Data-flow reachability into the left operand from the right, evaluated over SSA rather than
     * by {@link #test}, which always returns false for it.
     */
    FLOWS_FROM,
    /**
     * Subtype relation between two types, resolved by the query engine rather than by
     * {@link #test}, which always returns false for it.
     */
    SUBTYPE_OF;

    /**
     * Data-flow relations whose right operand is an accessor, evaluated over SSA (not {@link #test}).
     *
     * @return true for FLOWS_TO and FLOWS_FROM
     */
    public boolean isRelational()
    {
        return this == FLOWS_TO || this == FLOWS_FROM;
    }

    /**
     * Applies this operator, dispatching on {@link ValueKind}.
     *
     * @param lhs the left operand
     * @param rhs the right operand
     * @return true if the operator holds between the two values
     */
    public boolean test(Value lhs, Value rhs)
    {
        if (lhs == null || rhs == null || lhs.kind() == ValueKind.ABSENT || rhs.kind() == ValueKind.ABSENT)
        {
            return false;
        }
        switch (this)
        {
            case EQ:          return equal(lhs, rhs);
            case NEQ:         return !equal(lhs, rhs);
            case LT:          return numericCompare(lhs, rhs, -1, false);
            case LTE:         return numericCompare(lhs, rhs, -1, true);
            case GT:          return numericCompare(lhs, rhs, 1, false);
            case GTE:         return numericCompare(lhs, rhs, 1, true);
            case MATCHES:     return matches(lhs, rhs);
            case CONTAINS:    return contains(lhs, rhs);
            case STARTS_WITH: return bothStrings(lhs, rhs) && str(lhs).startsWith(str(rhs));
            case ENDS_WITH:   return bothStrings(lhs, rhs) && str(lhs).endsWith(str(rhs));
            case IN:          return rhs.kind() == ValueKind.SET && memberOf((Value.SetValue) rhs, lhs);
            case FLOWS_TO:
            case FLOWS_FROM:
            case SUBTYPE_OF:
            default:          return false;
        }
    }

    private static boolean equal(Value a, Value b)
    {
        if (a.kind() == ValueKind.TYPE || b.kind() == ValueKind.TYPE)
        {
            String ta = typeString(a);
            String tb = typeString(b);
            return ta != null && tb != null && TypeNames.canonical(ta).equals(TypeNames.canonical(tb));
        }
        if (isNumeric(a) && isNumeric(b))
        {
            return Double.compare(toDouble(a), toDouble(b)) == 0;
        }
        if (a.kind() == ValueKind.STRING && b.kind() == ValueKind.STRING)
        {
            return str(a).equals(str(b));
        }
        if (a.kind() == ValueKind.BOOL && b.kind() == ValueKind.BOOL)
        {
            return ((Value.BoolValue) a).get() == ((Value.BoolValue) b).get();
        }
        return a.kind() == ValueKind.NULL && b.kind() == ValueKind.NULL;
    }

    private static boolean numericCompare(Value a, Value b, int sign, boolean orEqual)
    {
        if (!isNumeric(a) || !isNumeric(b))
        {
            return false;
        }
        int cmp = Double.compare(toDouble(a), toDouble(b));
        if (cmp == 0)
        {
            return orEqual;
        }
        return Integer.signum(cmp) == sign;
    }

    private static boolean matches(Value lhs, Value rhs)
    {
        if (rhs.kind() != ValueKind.REGEX)
        {
            return false;
        }
        String s = stringish(lhs);
        if (s == null)
        {
            return false;
        }
        Pattern p = ((Value.RegexValue) rhs).get();
        return p.matcher(s).find();
    }

    private static boolean contains(Value lhs, Value rhs)
    {
        if (lhs.kind() == ValueKind.SET)
        {
            return memberOf((Value.SetValue) lhs, rhs);
        }
        return bothStrings(lhs, rhs) && str(lhs).contains(str(rhs));
    }

    private static boolean memberOf(Value.SetValue set, Value member)
    {
        for (Value v : set.get())
        {
            if (EQ.test(v, member))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumeric(Value v)
    {
        return v.kind() == ValueKind.INT || v.kind() == ValueKind.REAL;
    }

    private static double toDouble(Value v)
    {
        return v.kind() == ValueKind.INT ? ((Value.IntValue) v).get() : ((Value.RealValue) v).get();
    }

    private static boolean bothStrings(Value a, Value b)
    {
        return a.kind() == ValueKind.STRING && b.kind() == ValueKind.STRING;
    }

    private static String str(Value v)
    {
        return ((Value.StrValue) v).get();
    }

    /**
     * A string view usable as a regex/contains subject: strings and type names.
     */
    private static String stringish(Value v)
    {
        if (v.kind() == ValueKind.STRING)
        {
            return ((Value.StrValue) v).get();
        }
        if (v.kind() == ValueKind.TYPE)
        {
            return ((Value.TypeValue) v).get();
        }
        return null;
    }

    private static String typeString(Value v)
    {
        if (v.kind() == ValueKind.TYPE)
        {
            return ((Value.TypeValue) v).get();
        }
        if (v.kind() == ValueKind.STRING)
        {
            return ((Value.StrValue) v).get();
        }
        return null;
    }
}
