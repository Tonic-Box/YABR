package com.tonic.analysis.query.eval;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.query.ast.Condition;
import com.tonic.analysis.query.parser.ConditionParser;
import com.tonic.type.AccessFlags;
import com.tonic.type.MethodHandle;
import com.tonic.type.TypeDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        return matches(classFile, methodName, query);
    }

    private static boolean matches(ClassFile cf, String methodName, String query) throws Exception {
        Condition condition = ConditionParser.parse(query);
        MethodEntry method = cf.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow();
        ConditionEvaluator evaluator = new ConditionEvaluator(DefaultAttributes.create());
        EvalContext ctx = new EvalContext(cf, method, new EvidenceCollector());
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
    void dynamicSiteExposesKind() throws Exception {
        // `kind` mirrors `site` on a dynamic subject, so it resolves on the site itself, not only on bsmArgs.
        assertTrue(matches("concat", "has indy where (kind == \"indy\")"));
    }

    @Test
    void condyBootstrapArgumentIsQueryableByKindAndBsm() throws Exception {
        // Build a concat whose `{const}` is supplied by a CONSTANT_Dynamic - the exact shape that previously
        // returned no matches because a condy bsmArg (a dynamic subject) had no `kind` attribute.
        ClassFile cf = ClassBuilder.create("CondyArg")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "concat", "(I)V")
                    .code()
                        .iload(0)
                        .invokedynamic("makeConcatWithConstants", "(I)Ljava/lang/String;", CONCAT_BSM, "x")
                        .pop()
                        .vreturn()
                    .end()
                .end()
                .build();

        ConstPool cp = cf.getConstPool();
        int condyBsm = cf.addBootstrapMethod(
                cp.getIndexOf(cp.findOrAddMethodHandle(MethodHandle.H_INVOKESTATIC,
                        "java/lang/invoke/ConstantBootstraps", "getStaticFinal",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;")),
                List.of());
        int condyCp = cp.getIndexOf(cp.findOrAddConstantDynamic(condyBsm, "VALUE", "I"));

        MethodEntry concat = cf.getMethods().stream()
                .filter(m -> m.getName().equals("concat")).findFirst().orElseThrow();
        int concatBsm = new CodeWriter(concat).getInstructionList().stream()
                .filter(InvokeDynamicInstruction.class::isInstance)
                .map(i -> ((InvokeDynamicInstruction) i).getBootstrapMethodAttrIndex())
                .findFirst().orElseThrow();
        cf.getBootstrapMethodsAttribute().getBootstrapMethods().get(concatBsm).getBootstrapArguments().add(condyCp);

        assertTrue(matches(cf, "concat", "has indy where (has bsmArg where (kind == \"condy\"))"));
        assertTrue(matches(cf, "concat",
                "has indy where (has bsmArg where (kind == \"condy\" and bsmName == \"getStaticFinal\"))"));
        assertFalse(matches(cf, "concat", "has indy where (has bsmArg where (kind == \"condy\" and bsmName == \"nope\"))"));
    }

    @Test
    void methodWithoutIndyDoesNotMatch() throws Exception {
        assertFalse(matches("body", "has indy where (site == \"indy\")"));
    }
}
