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
        for (PlantumlObject obj: ast.getObjects()){
            obj.accept(this, indent + indentBase);
        }
        this.out.append('\n');
        for (PlantumlConnection conn: ast.getConnections()){
            conn.accept(this, indent + indentBase);
        }
        this.out.append(indent).append("@enduml").append("\n");
        return null;
    }

    @Override
    public Void visit(PlantumlObject plantumlObject, String s) throws IOException {
        this.out.append(s).append("object ").append(plantumlObject.getName());
        if (plantumlObject.getPropertyGroups().length > 0) {
            this.out.append(" {\n");
            for (PlantumlPropertyGroup grp: plantumlObject.getPropertyGroups()){
                grp.accept(this, s + indentBase);
            }
            this.out.append(s).append("}\n");
        } else {
            this.out.append("\n");
        }
        return null;
    }

    @Override
    public Void visit(PlantumlPropertyGroup plantumlPropertyGroup, String s) throws IOException {
        this.out.append(s).append(".. ").append(plantumlPropertyGroup.getName()).append(" ..").append("\n");
        for (PlantumlProperty prop: plantumlPropertyGroup.getProperties()){
            prop.accept(this, s);
        }
        return null;
    }

    @Override
    public Void visit(PlantumlProperty plantumlProperty, String s) throws IOException {
        this.out.append(s).append("* ").append(plantumlProperty.getProp()).append('\n');
        return null;
    }

    @Override
    public Void visit(PlantumlConnection plantumlConnection, String s) throws IOException {
        this.out.append(s)
                .append(plantumlConnection.getFromObj())
                .append(" ").append(plantumlConnection.getFromConn()).append(plantumlConnection.getLineChar()).append(plantumlConnection.getToConn())
                .append(" ")
                .append(plantumlConnection.getToObj());
        if (plantumlConnection.getLabel().length() > 0){
            this.out.append(" ")
                    .append(": ")
                    .append(plantumlConnection.getLabel());
        }
        this.out.append('\n');
        return null;
    }
}
