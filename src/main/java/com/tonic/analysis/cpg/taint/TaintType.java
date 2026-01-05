package com.tonic.analysis.cpg.taint;

public enum TaintType {
    USER_INPUT,
    FILE_INPUT,
    NETWORK,
    DATABASE,
    ENVIRONMENT,
    DESERIALIZATION,
    REFLECTION,
    CUSTOM
}
