package com.tonic.analysis.query.eval;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.query.ast.Condition;
import com.tonic.analysis.query.parser.ConditionParser;
import com.tonic.type.AccessFlags;
import com.tonic.type.MethodHandle;
import com.tonic.type.TypeDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that the query engine surfaces invokedynamic call sites and their bootstraps:
 * string concatenation (recipe + bsm name) and lambda metafactory. The fixture is built
 * programmatically so it does not depend on the test compiler's class-file version.
 */
class DynamicQueryTest {

    private static final MethodHandle CONCAT_BSM = new MethodHandle(MethodHandle.H_INVOKESTATIC,
            "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;");

    private static final MethodHandle LAMBDA_BSM = new MethodHandle(MethodHandle.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;");

    private static final String RECIPE = "xy";

    private static ClassFile classFile;

    @BeforeAll
    static void buildFixture() {
        classFile = ClassBuilder.create("Dyn")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "concat", "(I)V")
                    .code()
                        .iload(0)
                        .invokedynamic("makeConcatWithConstants", "(I)Ljava/lang/String;", CONCAT_BSM, RECIPE)
                        .pop()
                        .vreturn()
                    .end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "lambda", "()V")
                    .code()
                        .invokedynamic("run", "()Ljava/lang/Runnable;", LAMBDA_BSM,
                                TypeDescriptor.parse("()V"),
                                new MethodHandle(MethodHandle.H_INVOKESTATIC, "Dyn", "body", "()V"),
                                TypeDescriptor.parse("()V"))
                        .pop()
                        .vreturn()
                    .end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "body", "()V")
                    .code().vreturn().end()
                .end()
                .build();
    }

    private boolean matches(String methodName, String query) throws Exception {
        Condition condition = ConditionParser.parse(query);
        MethodEntry method = classFile.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        ConditionEvaluator evaluator = new ConditionEvaluator(DefaultAttributes.create());
        EvalContext ctx = new EvalContext(classFile, method, new EvidenceCollector());
        return evaluator.eval(condition, new Subject.MethodSubject(method, ctx));
    }

    @Test
    void matchesStringConcatBootstrapAndRecipe() throws Exception {
        assertTrue(matches("concat", "has indy where (category == \"stringconcat\")"));
        assertTrue(matches("concat", "has indy where (category == \"stringconcat\" and recipe contains \"x\")"));
        assertTrue(matches("concat", "has indy where (bsmName matches /makeConcat.*/)"));
        assertTrue(matches("concat", "has indy where (name == \"makeConcatWithConstants\" and site == \"indy\")"));
    }

    @Test
    void recipeRendersArgMarkerNotRawTag() throws Exception {
        assertTrue(matches("concat", "has indy where (recipe contains \"{arg}\")"));
        assertFalse(matches("concat", "has indy where (category == \"lambda\")"));
    }

    @Test
    void matchesLambdaBootstrap() throws Exception {
        assertTrue(matches("lambda", "has indy where (category == \"lambda\")"));
        assertTrue(matches("lambda", "has indy where (bsmOwner == \"java/lang/invoke/LambdaMetafactory\")"));
        assertFalse(matches("lambda", "has indy where (category == \"stringconcat\")"));
    }

    @Test
    void bootstrapArgsAreQueryable() throws Exception {
        assertTrue(matches("concat", "has indy where (has bsmArg where (kind == \"string\"))"));
        assertTrue(matches("lambda", "has indy where (has bsmArg where (kind == \"methodHandle\"))"));
    }

    @Test
    void methodWithoutIndyDoesNotMatch() throws Exception {
        assertFalse(matches("body", "has indy where (site == \"indy\")"));
    }
}
