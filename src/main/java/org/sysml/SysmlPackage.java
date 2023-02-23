package org.sysml;



import java.io.IOException;
import java.util.ArrayList;

/**
 * TODO: build out the DiagramElement taxonomy better
 */
public class SysmlPackage implements SysmlId, SysmlBlockDefElement, SysmlExpr {
    private final ArrayList<SysmlBlockDefElement> elements;
    private final String name;

    public SysmlPackage(String name, ArrayList<SysmlBlockDefElement> elements){
        this.elements = elements;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ArrayList<SysmlBlockDefElement> getElements(){
        return elements;
    }

    @Override
    public <A, B> B accept(SysmlExprVisitor<A, B> visitor, A a) throws IOException {
        return visitor.visit(this, a);
    }
}
