package com.tonic.analysis.verifier;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Verifier Tests")
public class VerifierTest {
    private ClassPool classPool;

    @BeforeEach
    void setUp() throws IOException {
        classPool = new ClassPool();
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {
        @Test
        @DisplayName("should create verifier with default config")
        void testDefaultConfig() {
            Verifier verifier = Verifier.builder().build();
            assertNotNull(verifier);
            assertNotNull(verifier.getConfig());
        }

        @Test
        @DisplayName("should create verifier with custom config")
        void testCustomConfig() {
            VerifierConfig config = VerifierConfig.builder()
                    .errorMode(VerifierConfig.ErrorMode.COLLECT_ALL)
                    .maxErrors(50)
                    .build();

            Verifier verifier = Verifier.builder()
                    .config(config)
                    .build();

            assertNotNull(verifier);
            assertEquals(50, verifier.getConfig().getMaxErrors());
        }

        @Test
        @DisplayName("should create verifier with fail-fast mode")
        void testFailFastMode() {
            Verifier verifier = Verifier.builder()
                    .failFast()
                    .build();

            assertTrue(verifier.getConfig().isFailFast());
        }

        @Test
        @DisplayName("should create verifier with collect-all mode")
        void testCollectAllMode() {
            Verifier verifier = Verifier.builder()
                    .collectAll()
                    .build();

            assertFalse(verifier.getConfig().isFailFast());
        }

        @Test
        @DisplayName("should create verifier with class pool")
        void testWithClassPool() {
            Verifier verifier = Verifier.builder()
                    .classPool(classPool)
                    .build();

            assertSame(classPool, verifier.getClassPool());
        }
    }

    @Nested
    @DisplayName("VerifierConfig Tests")
    class ConfigTests {
        @Test
        @DisplayName("should create default config with all verifications enabled")
        void testDefaultConfig() {
            VerifierConfig config = VerifierConfig.defaults();

            assertTrue(config.isVerifyStructure());
            assertTrue(config.isVerifyControlFlow());
        }

        @Test
        @DisplayName("should allow disabling specific verifications")
        void testDisableVerifications() {
            VerifierConfig config = VerifierConfig.builder()
                    .verifyStackMapTable(false)
                    .strictTypeChecking(false)
                    .build();

            assertFalse(config.isVerifyStackMapTable());
            assertFalse(config.isStrictTypeChecking());
        }

        @Test
        @DisplayName("should set max errors limit")
        void testMaxErrors() {
            VerifierConfig config = VerifierConfig.builder()
                    .maxErrors(10)
                    .build();

            assertEquals(10, config.getMaxErrors());
        }

        @Test
        @DisplayName("should treat warnings as errors when configured")
        void testTreatWarningsAsErrors() {
            VerifierConfig config = VerifierConfig.builder()
                    .treatWarningsAsErrors(true)
                    .build();

            assertTrue(config.isTreatWarningsAsErrors());
        }
    }

    @Nested
    @DisplayName("VerificationError Tests")
    class ErrorTests {
        @Test
        @DisplayName("should create error with all fields")
        void testCreateError() {
            VerificationError error = new VerificationError(
                    VerificationErrorType.INVALID_OPCODE,
                    42,
                    "Test error message"
            );

            assertEquals(VerificationErrorType.INVALID_OPCODE, error.getType());
            assertEquals(42, error.getBytecodeOffset());
            assertEquals("Test error message", error.getMessage());
            assertEquals(VerificationError.Severity.ERROR, error.getSeverity());
        }

        @Test
        @DisplayName("should create warning")
        void testCreateWarning() {
            VerificationError warning = new VerificationError(
                    VerificationErrorType.UNREACHABLE_CODE,
                    100,
                    "Unreachable code",
                    VerificationError.Severity.WARNING
            );

            assertEquals(VerificationError.Severity.WARNING, warning.getSeverity());
        }

        @Test
        @DisplayName("should add location to error")
        void testWithLocation() {
            VerificationError error = new VerificationError(
                    VerificationErrorType.STACK_OVERFLOW,
                    10,
                    "Stack overflow"
            );

            VerificationError located = error.withLocation("MyClass", "myMethod()V");

            assertEquals("MyClass", located.getClassName());
            assertEquals("myMethod()V", located.getMethodName());
        }
    }

    @Nested
    @DisplayName("VerificationResult Tests")
    class ResultTests {
        @Test
        @DisplayName("should create success result")
        void testSuccessResult() {
            VerificationResult result = VerificationResult.success("TestClass");

            assertTrue(result.isValid());
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("should create failure result")
        void testFailureResult() {
            VerificationError error = new VerificationError(
                    VerificationErrorType.INVALID_OPCODE,
                    0,
                    "Bad opcode"
            );

            VerificationResult result = VerificationResult.failure(
                    java.util.List.of(error),
                    "TestClass"
            );

            assertFalse(result.isValid());
            assertEquals(1, result.getErrors().size());
        }

        @Test
        @DisplayName("should format report")
        void testFormatReport() {
            VerificationError error = new VerificationError(
                    VerificationErrorType.STACK_UNDERFLOW,
                    25,
                    "Stack underflow"
            );

            VerificationResult result = VerificationResult.failure(
                    java.util.List.of(error),
                    "TestClass"
            );

            String report = result.formatReport();
            assertNotNull(report);
            assertTrue(report.contains("Stack underflow") || report.contains("error"));
        }
    }

    @Nested
    @DisplayName("ErrorCollector Tests")
    class CollectorTests {
        @Test
        @DisplayName("should collect all errors in collect mode")
        void testCollectAllMode() {
            VerifierConfig config = VerifierConfig.builder()
                    .errorMode(VerifierConfig.ErrorMode.COLLECT_ALL)
                    .build();

            ErrorCollector collector = ErrorCollector.forConfig(config);

            collector.addError(new VerificationError(VerificationErrorType.INVALID_OPCODE, 0, "Error 1"));
            collector.addError(new VerificationError(VerificationErrorType.INVALID_OPCODE, 1, "Error 2"));

            assertFalse(collector.shouldStop());
            assertEquals(2, collector.getErrors().size());
        }

        @Test
        @DisplayName("should stop on first error in fail-fast mode")
        void testFailFastMode() {
            VerifierConfig config = VerifierConfig.builder()
                    .errorMode(VerifierConfig.ErrorMode.FAIL_FAST)
                    .build();

            ErrorCollector collector = ErrorCollector.forConfig(config);

            collector.addError(new VerificationError(VerificationErrorType.INVALID_OPCODE, 0, "Error 1"));

            assertTrue(collector.shouldStop());
        }

        @Test
        @DisplayName("should respect max errors limit")
        void testMaxErrorsLimit() {
            VerifierConfig config = VerifierConfig.builder()
                    .errorMode(VerifierConfig.ErrorMode.COLLECT_ALL)
                    .maxErrors(2)
                    .build();

            ErrorCollector collector = ErrorCollector.forConfig(config);

            collector.addError(new VerificationError(VerificationErrorType.INVALID_OPCODE, 0, "Error 1"));
            collector.addError(new VerificationError(VerificationErrorType.INVALID_OPCODE, 1, "Error 2"));

            assertTrue(collector.shouldStop());
        }

        @Test
        @DisplayName("should collect warnings separately")
        void testWarningCollection() {
            VerifierConfig config = VerifierConfig.defaults();
            ErrorCollector collector = ErrorCollector.forConfig(config);

            collector.addWarning(new VerificationError(
                    VerificationErrorType.UNREACHABLE_CODE,
                    50,
                    "Dead code",
                    VerificationError.Severity.WARNING
            ));

            assertEquals(0, collector.getErrors().size());
            assertEquals(1, collector.getWarnings().size());
        }

        @Test
        @DisplayName("should track treatWarningsAsErrors configuration")
        void testWarningsAsErrorsConfig() {
            VerifierConfig config = VerifierConfig.builder()
                    .treatWarningsAsErrors(true)
                    .build();

            assertTrue(config.isTreatWarningsAsErrors());
        }
    }

    @Nested
    @DisplayName("VerificationErrorType Tests")
    class ErrorTypeTests {
        @Test
        @DisplayName("should have structural error types")
        void testStructuralErrorTypes() {
            assertNotNull(VerificationErrorType.INVALID_OPCODE);
            assertNotNull(VerificationErrorType.INVALID_OPERAND);
            assertNotNull(VerificationErrorType.INVALID_CONSTANT_POOL_INDEX);
            assertNotNull(VerificationErrorType.INVALID_BRANCH_TARGET);
        }

        @Test
        @DisplayName("should have type safety error types")
        void testTypeSafetyErrorTypes() {
            assertNotNull(VerificationErrorType.STACK_UNDERFLOW);
            assertNotNull(VerificationErrorType.STACK_OVERFLOW);
            assertNotNull(VerificationErrorType.TYPE_MISMATCH);
        }

        @Test
        @DisplayName("should have control flow error types")
        void testControlFlowErrorTypes() {
            assertNotNull(VerificationErrorType.UNREACHABLE_CODE);
            assertNotNull(VerificationErrorType.INVALID_EXCEPTION_HANDLER);
        }

        @Test
        @DisplayName("should have stackmap error types")
        void testStackMapErrorTypes() {
            assertNotNull(VerificationErrorType.MISSING_STACKMAP_FRAME);
            assertNotNull(VerificationErrorType.FRAME_TYPE_MISMATCH);
        }
    }
}
