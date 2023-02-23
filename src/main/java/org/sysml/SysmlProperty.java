package org.sysml;

import org.clafer.common.Check;

/**
 * SysML Property
 *
 * <property> ::= [<block-visibility>] [‘/’] [<property-type-keywords>] <name-string> <property-declaration>
 *
 * TODO: do the declaration
 */
public class SysmlProperty implements SysmlId, SysmlBlockDefElement {

    private final String name;

    public SysmlProperty(SysmlBlockVisibility blockVis, SysmlPropertyType propType, String name){
        this.name = Check.notNull(name);
    }
    @Override
    public String getName() {
        return name;
    }
}
