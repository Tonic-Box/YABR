package com.tonic.analysis.source.ast.decl;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public final class ImportDecl implements ASTNode {

    @Setter
    private String name;
    private final boolean isStatic;
    private final boolean isWildcard;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public ImportDecl(String name, boolean isStatic, boolean isWildcard, SourceLocation location) {
        this.name = name;
        this.isStatic = isStatic;
        this.isWildcard = isWildcard;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
    }

    public ImportDecl(String name, boolean isStatic, boolean isWildcard) {
        this(name, isStatic, isWildcard, SourceLocation.UNKNOWN);
    }

    public ImportDecl(String name) {
        this(name, false, false);
    }

    public static ImportDecl regular(String name) {
        return new ImportDecl(name, false, false);
    }

    public static ImportDecl staticImport(String name) {
        return new ImportDecl(name, true, false);
    }

    public static ImportDecl wildcard(String packageName) {
        return new ImportDecl(packageName, false, true);
    }

    public static ImportDecl staticWildcard(String className) {
        return new ImportDecl(className, true, true);
    }

    public String getPackageName() {
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) return "";
        return name.substring(0, lastDot);
    }

    public String getSimpleName() {
        if (isWildcard) return "*";
        int lastDot = name.lastIndexOf('.');
        if (lastDot < 0) return name;
        return name.substring(lastDot + 1);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("import ");
        if (isStatic) sb.append("static ");
        sb.append(name);
        if (isWildcard) sb.append(".*");
        return sb.toString();
    }
}
