package org.sysml;


import java.util.ArrayList;

/**
 * TODO: build out the DiagramElement taxonomy better
 */
public class SysmlPackage implements SysmlId, SysmlBlockDefElement {
    private final ArrayList<SysmlBlockDefElement> elements;
    private final String name;

    public SysmlPackage(String name, ArrayList<SysmlBlockDefElement> elements){
        this.elements = elements;
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
