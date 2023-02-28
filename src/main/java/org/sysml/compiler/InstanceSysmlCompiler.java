package org.sysml.compiler;

import org.clafer.ast.AstClafer;
import org.clafer.ast.AstRef;
import org.clafer.instance.InstanceClafer;
import org.sysml.ast.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InstanceSysmlCompiler {

    private ArrayList<String> processedClafers;

    public InstanceSysmlCompiler(){
        this.processedClafers = new ArrayList<String>();
    }

    public String getPropertyId(String name){
        if (name.startsWith("c0_")){
            return name.substring(3);
        } else {
            return name;
        }
    }

    int getMultiplicity(InstanceClafer model, AstClafer clafer){
        int count = 0;
        if (model.getType().getName().equals(clafer.getName())){
            count++;
        }
        for (InstanceClafer child : model.getChildren()) {
            count += getMultiplicity(child, clafer);
        }
        return count;
    }

    ArrayList<String> _getSuperClafers(AstClafer clafer, ArrayList<String> hier){
        if (clafer.getSuperClafer() == null) {
            return hier;
        } else {
            hier.add(getPropertyId(clafer.getSuperClafer().getName()));
            return _getSuperClafers(clafer.getSuperClafer(), hier);
        }
    }

    public String[] getSuperClafers(AstClafer clafer){
        ArrayList<String> clafers = _getSuperClafers(clafer, new ArrayList<String>());
        String[] clafers_arr = new String[clafers.size()];
        return clafers.toArray(clafers_arr);
    }

    public SysmlProperty compile(InstanceClafer model, InstanceClafer topLevelModel) {
        String propertyName = getPropertyId(model.getType().getName());
        String[] superClafers = getSuperClafers(model.getType());
        List hierarchy = Arrays.asList(superClafers);
        ArrayList<SysmlBlockDefElement> children = new ArrayList<SysmlBlockDefElement>();
        for (InstanceClafer child : model.getChildren()) {
            if (!processedClafers.contains(child.getType().getName())) {
                SysmlProperty cchild = compile(child, topLevelModel);
                if (!cchild.getPropertyType().getName().equals("<skip>")) {
                    children.add(compile(child, topLevelModel));
                } else {
                    SysmlBlockDefElement[] schildren = cchild.getElements();
                    children.addAll(new ArrayList<SysmlBlockDefElement>(Arrays.asList(schildren)));
                }
            }
        }

        //Object ref = model.getType().getRef();
        //if (ref instanceof AstRef){
        //    System.out.println(((AstRef) ref).getTargetType().getName());
        //}

        String propName = "";
        String[] superTypes = new String[0];
        if (hierarchy.contains("Part")) {
            propName = "part";
            superTypes = Arrays.copyOfRange(superClafers, 0, superClafers.length-2);
        } else {
            propName = "<skip>";
        }
        int multiplicity = getMultiplicity(topLevelModel, model.getType());
        processedClafers.add(model.getType().getName());
        return new SysmlProperty(
                new SysmlBlockVisibility(SysmlVisibilityOption.PLUS),
                new SysmlPropertyType(propName),
                propertyName,
                children.toArray(new SysmlBlockDefElement[children.size()]),
                superTypes,
                multiplicity
        );
    }

}
