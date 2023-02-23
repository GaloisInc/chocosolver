package org.sysml;

import org.clafer.common.Check;

import java.util.ArrayList;

/**
 * SysML Property
 *
 * <property> ::= [<block-visibility>] [‘/’] [<property-type-keywords>] <name-string> <property-declaration>
 *
 * TODO: do the declaration
 */
public class SysmlProperty implements SysmlBlockDefElement {

    private final String name;
    private final SysmlPropertyType prop;

    private final SysmlBlockDefElement[] elements;

    public SysmlProperty(SysmlBlockVisibility blockVis, SysmlPropertyType propType, String name, SysmlBlockDefElement[] elements){
        this.name = Check.notNull(name);
        this.prop = propType;
        this.elements = elements;
    }

    public SysmlProperty(SysmlBlockVisibility blockVis, SysmlPropertyType propType, String name){
        this.name = Check.notNull(name);
        this.prop = propType;
        this.elements = new SysmlBlockDefElement[0];
    }
    @Override
    public String getName() {
        return name;
    }

    public  SysmlPropertyType getPropertyType(){
        return prop;
    }

    public SysmlBlockDefElement[] getElements() {
        return elements;
    }

    @Override
    public <A, B> B accept(SysmlExprVisitor<A, B> visitor, A a) {
        return visitor.visit(this, a);
    }
}
