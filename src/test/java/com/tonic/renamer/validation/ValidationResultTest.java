package com.tonic.renamer.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResultTest {

    private ValidationResult result;

    @BeforeEach
    void setUp() {
        result = new ValidationResult();
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void newResultIsValid() {
            assertTrue(result.isValid());
            assertFalse(result.hasErrors());
        }

        @Test
        void addingErrorMakesInvalid() {
            result.addError("Test error");

            assertFalse(result.isValid());
            assertTrue(result.hasErrors());
        }

        @Test
        void errorsAreStoredAndRetrievable() {
            result.addError("Error 1");
            result.addError("Error 2");

            assertEquals(2, result.getErrorCount());
            assertEquals(2, result.getErrors().size());
            assertTrue(result.getErrors().contains("Error 1"));
            assertTrue(result.getErrors().contains("Error 2"));
        }

        @Test
        void getErrorsReturnsUnmodifiableList() {
            result.addError("Test error");

            assertThrows(UnsupportedOperationException.class, () ->
                result.getErrors().add("Another error")
            );
        }
    }

    @Nested
    class WarningHandlingTests {

        @Test
        void newResultHasNoWarnings() {
            assertFalse(result.hasWarnings());
        }

        @Test
        void addingWarningDoesNotMakeInvalid() {
            result.addWarning("Test warning");

            assertTrue(result.isValid());
            assertTrue(result.hasWarnings());
        }

        @Test
        void warningsAreStoredAndRetrievable() {
            result.addWarning("Warning 1");
            result.addWarning("Warning 2");

            assertEquals(2, result.getWarningCount());
            assertEquals(2, result.getWarnings().size());
            assertTrue(result.getWarnings().contains("Warning 1"));
            assertTrue(result.getWarnings().contains("Warning 2"));
        }

        @Test
        void getWarningsReturnsUnmodifiableList() {
            result.addWarning("Test warning");

            assertThrows(UnsupportedOperationException.class, () ->
                result.getWarnings().add("Another warning")
            );
        }
    }

    @Nested
    class MergeTests {

        @Test
        void mergeAddsErrorsFromOther() {
            result.addError("Error 1");

            ValidationResult other = new ValidationResult();
            other.addError("Error 2");

            result.merge(other);

            assertEquals(2, result.getErrorCount());
            assertTrue(result.getErrors().contains("Error 1"));
            assertTrue(result.getErrors().contains("Error 2"));
        }

        @Test
        void mergeAddsWarningsFromOther() {
            result.addWarning("Warning 1");

            ValidationResult other = new ValidationResult();
            other.addWarning("Warning 2");

            result.merge(other);

            assertEquals(2, result.getWarningCount());
            assertTrue(result.getWarnings().contains("Warning 1"));
            assertTrue(result.getWarnings().contains("Warning 2"));
        }

        @Test
        void mergeEmptyResultHasNoEffect() {
            result.addError("Error 1");
            result.addWarning("Warning 1");

            ValidationResult other = new ValidationResult();
            result.merge(other);

            assertEquals(1, result.getErrorCount());
            assertEquals(1, result.getWarningCount());
        }
    }

    @Nested
    class ReportTests {

        @Test
        void reportContainsErrors() {
            result.addError("Error 1");
            result.addError("Error 2");

            String report = result.getReport();
            assertTrue(report.contains("Error 1"));
            assertTrue(report.contains("Error 2"));
            assertTrue(report.contains("Errors:"));
        }

        @Test
        void reportContainsWarnings() {
            result.addWarning("Warning 1");
            result.addWarning("Warning 2");

            String report = result.getReport();
            assertTrue(report.contains("Warning 1"));
            assertTrue(report.contains("Warning 2"));
            assertTrue(report.contains("Warnings:"));
        }

        @Test
        void reportIndicatesNoIssuesWhenEmpty() {
            String report = result.getReport();
            assertTrue(report.contains("No errors or warnings"));
        }

        @Test
        void reportContainsBothErrorsAndWarnings() {
            result.addError("Error 1");
            result.addWarning("Warning 1");

            String report = result.getReport();
            assertTrue(report.contains("Errors:"));
            assertTrue(report.contains("Error 1"));
            assertTrue(report.contains("Warnings:"));
            assertTrue(report.contains("Warning 1"));
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void toStringIndicatesValidWhenNoErrors() {
            String str = result.toString();
            assertTrue(str.contains("VALID"));
        }

        @Test
        void toStringIndicatesInvalidWhenErrors() {
            result.addError("Test error");

            String str = result.toString();
            assertTrue(str.contains("INVALID"));
            assertTrue(str.contains("1 error"));
        }

        @Test
        void toStringShowsWarningCount() {
            result.addWarning("Warning 1");
            result.addWarning("Warning 2");

            String str = result.toString();
            assertTrue(str.contains("2 warning"));
        }

        @Test
        void toStringShowsBothErrorAndWarningCounts() {
            result.addError("Error 1");
            result.addWarning("Warning 1");

            String str = result.toString();
            assertTrue(str.contains("1 error"));
            assertTrue(str.contains("1 warning"));
        }
    }
}
