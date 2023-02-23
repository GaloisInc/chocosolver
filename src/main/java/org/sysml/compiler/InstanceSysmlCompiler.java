package org.sysml.compiler;

import org.clafer.instance.InstanceClafer;
import org.sysml.ast.*;

import java.util.ArrayList;

public class InstanceSysmlCompiler {
    public SysmlProperty compile(InstanceClafer model) {
        String propertyName = model.getType().getName();
        ArrayList<SysmlBlockDefElement> children = new ArrayList<SysmlBlockDefElement>();
        for (InstanceClafer child : model.getChildren()) {
            children.add(compile(child));
        }
        return new SysmlProperty(new SysmlBlockVisibility(SysmlVisibilityOption.PLUS), new SysmlPropertyType("part"), propertyName, children.toArray(new SysmlBlockDefElement[children.size()]));
    }

}
