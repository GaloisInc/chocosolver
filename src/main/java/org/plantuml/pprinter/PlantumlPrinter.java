package org.plantuml.pprinter;

import org.plantuml.ast.*;

import java.io.IOException;

/**
 * PlantUML -> Text
 *
 * Visits the PlantUML AST and generates text output to an Appendable stream
 */
public class PlantumlPrinter implements PlantumlExprVisitor<String, Void> {
    private final String indentBase;
    private final Appendable out;

    public PlantumlPrinter(Appendable out) {
        this.out = out;
        this.indentBase = "  ";
    }

    // implement the visitor
    @Override
    public Void visit(PlantumlProgram ast, String indent) throws IOException {
        this.out.append(indent).append("@startuml").append("\n");
        this.out.append(indent).append("@enduml").append("\n");
        return null;
    }

    @Override
    public Void visit(PlantumlObject plantumlObject, String s) throws IOException {
        return null;
    }

    @Override
    public Void visit(PlantumlPropertyGroup plantumlPropertyGroup, String s) throws IOException {
        return null;
    }

    @Override
    public Void visit(PlantumlProperty plantumlProperty, String s) throws IOException {
        return null;
    }
}
