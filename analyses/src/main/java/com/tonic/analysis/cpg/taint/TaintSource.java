package com.tonic.analysis.cpg.taint;

import java.util.regex.Pattern;

public class TaintSource {

    private final String name;
    private final String ownerPattern;
    private final String methodPattern;
    private final String descriptorPattern;
    private final int taintedArgumentIndex;
    private final boolean taintsReturnValue;
    private final TaintType taintType;

    private transient Pattern compiledOwnerPattern;
    private transient Pattern compiledMethodPattern;
    private transient Pattern compiledDescriptorPattern;

    private TaintSource(Builder builder) {
        this.name = builder.name;
        this.ownerPattern = builder.ownerPattern;
        this.methodPattern = builder.methodPattern;
        this.descriptorPattern = builder.descriptorPattern;
        this.taintedArgumentIndex = builder.taintedArgumentIndex;
        this.taintsReturnValue = builder.taintsReturnValue;
        this.taintType = builder.taintType;
    }

    public String getName() {
        return name;
    }

    public String getOwnerPattern() {
        return ownerPattern;
    }

    public String getMethodPattern() {
        return methodPattern;
    }

    public String getDescriptorPattern() {
        return descriptorPattern;
    }

    public int getTaintedArgumentIndex() {
        return taintedArgumentIndex;
    }

    public boolean isTaintsReturnValue() {
        return taintsReturnValue;
    }

    public TaintType getTaintType() {
        return taintType;
    }

    public boolean matchesOwner(String owner) {
        if (ownerPattern == null) return true;
        if (compiledOwnerPattern == null) {
            compiledOwnerPattern = Pattern.compile(ownerPattern);
        }
        return compiledOwnerPattern.matcher(owner).matches();
    }

    public boolean matchesMethod(String method) {
        if (methodPattern == null) return true;
        if (compiledMethodPattern == null) {
            compiledMethodPattern = Pattern.compile(methodPattern);
        }
        return compiledMethodPattern.matcher(method).matches();
    }

    public boolean matchesDescriptor(String descriptor) {
        if (descriptorPattern == null) return true;
        if (compiledDescriptorPattern == null) {
            compiledDescriptorPattern = Pattern.compile(descriptorPattern);
        }
        return compiledDescriptorPattern.matcher(descriptor).matches();
    }

    public boolean matches(String owner, String method, String descriptor) {
        return matchesOwner(owner) && matchesMethod(method) && matchesDescriptor(descriptor);
    }

    public static TaintSource httpParameter() {
        return TaintSource.builder()
            .name("HTTP Parameter")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getParameter|getParameterValues|getParameterMap")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource httpHeader() {
        return TaintSource.builder()
            .name("HTTP Header")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getHeader|getHeaders")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource httpCookie() {
        return TaintSource.builder()
            .name("HTTP Cookie")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getCookies")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource consoleInput() {
        return TaintSource.builder()
            .name("Console Input")
            .ownerPattern("java/util/Scanner")
            .methodPattern("next.*|hasNext.*")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource fileRead() {
        return TaintSource.builder()
            .name("File Read")
            .ownerPattern("java/io/(FileInputStream|BufferedReader|FileReader)")
            .methodPattern("read.*")
            .taintsReturnValue(true)
            .taintType(TaintType.FILE_INPUT)
            .build();
    }

    public static TaintSource environmentVariable() {
        return TaintSource.builder()
            .name("Environment Variable")
            .ownerPattern("java/lang/System")
            .methodPattern("getenv|getProperty")
            .taintsReturnValue(true)
            .taintType(TaintType.ENVIRONMENT)
            .build();
    }

    public static TaintSource databaseQuery() {
        return TaintSource.builder()
            .name("Database Query")
            .ownerPattern("java/sql/ResultSet")
            .methodPattern("get.*")
            .taintsReturnValue(true)
            .taintType(TaintType.DATABASE)
            .build();
    }

    public static TaintSource networkInput() {
        return TaintSource.builder()
            .name("Network Input")
            .ownerPattern("java/net/(Socket|URLConnection)")
            .methodPattern("getInputStream")
            .taintsReturnValue(true)
            .taintType(TaintType.NETWORK)
            .build();
    }

    public static TaintSource custom(String name, String owner, String method, TaintType type) {
        return TaintSource.builder()
            .name(name)
            .ownerPattern(owner)
            .methodPattern(method)
            .taintsReturnValue(true)
            .taintType(type)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("TaintSource[%s: %s.%s]", name, ownerPattern, methodPattern);
    }

    public static class Builder {
        private String name;
        private String ownerPattern;
        private String methodPattern;
        private String descriptorPattern;
        private int taintedArgumentIndex;
        private boolean taintsReturnValue;
        private TaintType taintType;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ownerPattern(String ownerPattern) {
            this.ownerPattern = ownerPattern;
            return this;
        }

        public Builder methodPattern(String methodPattern) {
            this.methodPattern = methodPattern;
            return this;
        }

        public Builder descriptorPattern(String descriptorPattern) {
            this.descriptorPattern = descriptorPattern;
            return this;
        }

        public Builder taintedArgumentIndex(int taintedArgumentIndex) {
            this.taintedArgumentIndex = taintedArgumentIndex;
            return this;
        }

        public Builder taintsReturnValue(boolean taintsReturnValue) {
            this.taintsReturnValue = taintsReturnValue;
            return this;
        }

        public Builder taintType(TaintType taintType) {
            this.taintType = taintType;
            return this;
        }

        public TaintSource build() {
            return new TaintSource(this);
        }
    }
}
