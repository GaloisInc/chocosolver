package org.sysml.ast;

import org.clafer.common.Check;

import java.io.IOException;

public class SysmlAnnotation implements SysmlExpr, SysmlId {
    private String name;

    private Object ref;

    public SysmlAnnotation(String name, Object ref) {
        this.name = name;
        this.ref = ref;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getRef() {
        return ref.toString();
    }
    @Override
    public <A, B> B accept(SysmlExprVisitor<A, B> visitor, A a) throws IOException {
        return visitor.visit(this, a);
    }
}
