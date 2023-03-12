package org.plantuml.ast;

import java.io.IOException;

public class PlantumlProperty implements PlantumlExpr {
    private final String prop;

    public PlantumlProperty(String prop) {
        this.prop = prop;
    }

    public String getProp(){
        return prop;
    }

    @Override
    public <A, B> B accept(PlantumlExprVisitor<A, B> visitor, A a) throws IOException {
        return visitor.visit(this, a);
    }
}
