package org.sysml;

import org.clafer.common.Check;

/**
 * SysML Property
 *
 * <property> ::= [<block-visibility>] [‘/’] [<property-type-keywords>] <name-string> <property-declaration>
 *
 * TODO: do the declaration
 */
public class SysmlProperty implements SysmlId, SysmlBlockDefElement, SysmlExpr {

    private final String name;
    private final SysmlPropertyType prop;

    public SysmlProperty(SysmlBlockVisibility blockVis, SysmlPropertyType propType, String name){
        this.name = Check.notNull(name);
        this.prop = propType;
    }
    @Override
    public String getName() {
        return name;
    }

    public  SysmlPropertyType getPropertyType(){
        return prop;
    }

    @Override
    public <A, B> B accept(SysmlExprVisitor<A, B> visitor, A a) {
        return visitor.visit(this, a);
    }
}
