package com.tonic.analysis.verifier.type;

import com.tonic.analysis.frame.VerificationType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two checks that constrain an array load's element type and an invocation's receiver.
 * Both are deliberately permissive where the answer is unknowable: an unreadable element type, an
 * unresolvable owner and an interface owner are all accepted, so tightening them cannot reject
 * bytecode the verifier previously allowed for lack of information.
 */
class TypeConstraintWiringTest
{

    private ClassPool pool;
    private TypeConstraint constraint;

    private static VerificationType obj(String name)
    {
        return VerificationType.object(name, 0);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        pool = TestUtils.emptyPool();
        constraint = new TypeConstraint(pool);
    }

    @Test
    void anArrayLoadMustMatchTheElementTypeTheOpcodeExpects()
    {
        assertTrue(constraint.isArrayLoadValid(obj("[I"), VerificationType.INTEGER), "iaload from int[]");
        assertTrue(constraint.isArrayLoadValid(obj("[J"), VerificationType.LONG), "laload from long[]");
        assertTrue(constraint.isArrayLoadValid(obj("[F"), VerificationType.FLOAT), "faload from float[]");
        assertTrue(constraint.isArrayLoadValid(obj("[D"), VerificationType.DOUBLE), "daload from double[]");

        assertFalse(constraint.isArrayLoadValid(obj("[I"), VerificationType.LONG),
            "laload from an int[] loads the wrong width and must be rejected");
        assertFalse(constraint.isArrayLoadValid(obj("[Ljava/lang/String;"), VerificationType.INTEGER),
            "iaload from a reference array must be rejected");
        assertFalse(constraint.isArrayLoadValid(obj("[D"), VerificationType.FLOAT),
            "faload from a double[] must be rejected");
    }

    @Test
    void theSubWordArraysAllLoadAsInt()
    {
        for (String descriptor : new String[]{"[Z", "[B", "[C", "[S"})
        {
            assertTrue(constraint.isArrayLoadValid(obj(descriptor), VerificationType.INTEGER),
                descriptor + " loads as an int");
            assertFalse(constraint.isArrayLoadValid(obj(descriptor), VerificationType.LONG),
                descriptor + " does not load as a long");
        }
    }

    @Test
    void referenceAndNestedArrayElementsAreCheckedAsReferences()
    {
        assertTrue(constraint.isArrayLoadValid(obj("[Ljava/lang/String;"), obj("java/lang/Object")),
            "a String element is assignable to Object");
        assertTrue(constraint.isArrayLoadValid(obj("[[I"), obj("java/lang/Object")),
            "an int[] element is still a reference");
        assertFalse(constraint.isArrayLoadValid(obj("[[I"), VerificationType.INTEGER),
            "a nested array element is not an int");
    }

    @Test
    void anUnreadableOrAbsentElementTypeIsAccepted()
    {
        assertTrue(constraint.isArrayLoadValid(obj("java/lang/Object"), VerificationType.INTEGER),
            "an operand that names no array cannot be checked, so it is allowed");
        assertTrue(constraint.isArrayLoadValid(VerificationType.NULL, VerificationType.INTEGER),
            "aaload on null verifies");
        assertTrue(constraint.isArrayLoadValid(obj("[I"), null),
            "no expected element means only the operand is checked");
    }

    @Test
    void aNonReferenceArrayOperandIsStillRejected()
    {
        assertFalse(constraint.isArrayLoadValid(VerificationType.INTEGER, VerificationType.INTEGER));
        assertFalse(constraint.isArrayLoadValid(VerificationType.DOUBLE, VerificationType.DOUBLE));
    }

    @Test
    void aReceiverMustBeTheOwnerOrASubclassOfIt() throws Exception
    {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("java/lang/Object", access);
        pool.createNewClass("probe/Base", access);
        ClassFile derived = pool.createNewClass("probe/Derived", access);
        derived.setSuperClassName("probe/Base");
        pool.createNewClass("probe/Unrelated", access);

        assertTrue(constraint.isReceiverValid(obj("probe/Base"), "probe/Base"), "the owner itself");
        assertTrue(constraint.isReceiverValid(obj("probe/Derived"), "probe/Base"), "a subclass of the owner");
        assertFalse(constraint.isReceiverValid(obj("probe/Unrelated"), "probe/Base"),
            "an unrelated class whose whole chain resolves is not a valid receiver");
    }

    @Test
    void anInterfaceOwnerIsNotConstrained() throws Exception
    {
        int classAccess = new AccessBuilder().setPublic().build();
        pool.createNewClass("probe/Standalone", classAccess);
        pool.createNewClass("probe/Iface", classAccess | AccessFlags.ACC_INTERFACE);

        assertTrue(constraint.isReceiverValid(obj("probe/Standalone"), "probe/Iface"),
            "the verifier does not constrain an interface receiver, so neither may this check");
    }

    @Test
    void aChainThatCannotBeFullyResolvedIsAccepted() throws Exception
    {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("probe/Base", access);
        pool.createNewClass("probe/Unrelated", access);

        assertTrue(constraint.isReceiverValid(obj("probe/Unrelated"), "probe/Base"),
            "the walk reaches a superclass absent from the pool, so the relation cannot be disproved");
    }

    @Test
    void unknowableReceiversAreAccepted() throws Exception
    {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("probe/Known", access);

        assertTrue(constraint.isReceiverValid(obj("probe/Known"), "probe/NotInThePool"),
            "an owner that cannot be resolved leaves the receiver unconstrained");
        assertTrue(constraint.isReceiverValid(VerificationType.NULL, "probe/Known"), "null receiver");
        assertTrue(constraint.isReceiverValid(VerificationType.UNINITIALIZED_THIS, "probe/Known"),
            "an uninitialised this is a valid receiver for its own constructor");
        assertTrue(constraint.isReceiverValid(obj("[I"), "java/lang/Object"),
            "an array receiver is valid for the methods it inherits");
        assertTrue(constraint.isReceiverValid(obj("probe/Known"), null),
            "no owner means only the receiver is checked");
    }

    @Test
    void aNonReferenceReceiverIsRejected() throws Exception
    {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("probe/Owner", access);

        assertFalse(constraint.isReceiverValid(VerificationType.INTEGER, "probe/Owner"));
        assertFalse(constraint.isReceiverValid(VerificationType.LONG, "probe/Owner"));
    }

    @Test
    void withNoClassPoolEveryReceiverIsAccepted()
    {
        TypeConstraint poolless = new TypeConstraint(null);

        assertTrue(poolless.isReceiverValid(obj("probe/Unrelated"), "probe/Base"),
            "without a pool no relation can be disproved");
    }
}
