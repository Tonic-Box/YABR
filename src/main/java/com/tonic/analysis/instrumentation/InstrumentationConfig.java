package com.tonic.analysis.instrumentation;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration holder for instrumentation settings.
 */
@Getter
@Setter
@Builder
public class InstrumentationConfig {

    /** Whether to skip abstract methods (default: true) */
    @Builder.Default
    private boolean skipAbstract = true;

    /** Whether to skip native methods (default: true) */
    @Builder.Default
    private boolean skipNative = true;

    /** Whether to skip synthetic methods (default: true) */
    @Builder.Default
    private boolean skipSynthetic = true;

    /** Whether to skip constructors (default: false) */
    @Builder.Default
    private boolean skipConstructors = false;

    /** Whether to skip static initializers (default: false) */
    @Builder.Default
    private boolean skipStaticInitializers = false;

    /** Whether to skip bridge methods (default: true) */
    @Builder.Default
    private boolean skipBridge = true;

    /** Whether to log instrumentation progress (default: false) */
    @Builder.Default
    private boolean verbose = false;

    /** Whether to fail on errors or continue (default: false - continue) */
    @Builder.Default
    private boolean failOnError = false;

    /**
     * Creates a default configuration.
     */
    public static InstrumentationConfig defaults() {
        return InstrumentationConfig.builder().build();
    }

    /**
     * Creates a configuration that instruments everything.
     */
    public static InstrumentationConfig instrumentAll() {
        return InstrumentationConfig.builder()
                .skipAbstract(false)
                .skipNative(false)
                .skipSynthetic(false)
                .skipConstructors(false)
                .skipStaticInitializers(false)
                .skipBridge(false)
                .build();
    }
}
