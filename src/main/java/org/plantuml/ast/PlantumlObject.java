package org.plantuml.ast;

import java.io.IOException;

public class PlantumlObject implements PlantumlExpr, PlantumlId {
    private final String name;
    private final PlantumlPropertyGroup[] propertyGroups;

    public PlantumlObject(String name, PlantumlPropertyGroup[] propertyGroups) {
        this.name = name;
        this.propertyGroups = propertyGroups;
    }

    public PlantumlPropertyGroup[] getPropertyGroups() {
        return propertyGroups;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <A, B> B accept(PlantumlExprVisitor<A, B> visitor, A a) throws IOException {
        return visitor.visit(this, a);
    }
}
