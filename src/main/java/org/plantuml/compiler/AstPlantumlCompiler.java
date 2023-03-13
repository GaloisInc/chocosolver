package org.plantuml.compiler;

import org.clafer.ast.AstAbstractClafer;
import org.clafer.ast.AstConcreteClafer;
import org.clafer.ast.AstConstraint;
import org.clafer.ast.AstModel;
import org.plantuml.ast.*;
import org.sysml.compiler.SysmlCompilerUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Clafer AST to PlantUML
 *
 * Note that this compilation doesn't require instances, so we don't need to run the solver
 * to compile.
 */
public class AstPlantumlCompiler {
    /**
     * collect all concrete clafers
     * @param concreteClafers concreteClafers held in a claferModel
     * @return ArrayList of all nested clafers (abstract included)
     */
    private ArrayList<PlantumlObject> getConcreteObjects(List<AstConcreteClafer> concreteClafers) {
        ArrayList<PlantumlObject> objs = new ArrayList<PlantumlObject>();

        for (AstConcreteClafer ast: concreteClafers) {
            ArrayList<PlantumlPropertyGroup> pgs = new ArrayList<PlantumlPropertyGroup>();

            ArrayList<PlantumlProperty> constrs = new ArrayList<PlantumlProperty>();
            for (AstConstraint constr: ast.getConstraints()) {
                constrs.add(new PlantumlProperty(constr.toString()));
            }

            if (constrs.size() > 0){
                pgs.add(new PlantumlPropertyGroup("Constraints", constrs.toArray(new PlantumlProperty[0])));
            }

            // create an object and add it
            PlantumlObject obj = new PlantumlObject(
                    SysmlCompilerUtils.getPropertyId(ast.getName()),
                    pgs.toArray(new PlantumlPropertyGroup[0])
            );
            objs.add(obj);

            // add all of its children
            // TODO: check for collisions?
            //objs.addAll(getAbstractObjects(ast.getAbstractChildren()));
            objs.addAll(getConcreteObjects(ast.getChildren()));
        }
        return objs;
    }

    /**
     * collect all abstract clafers (give them an abstract attribute)
     * @param abstractClafers abstractClafers held in a claferModel
     * @return ArrayList of all nested clafers (concrete included)
     */
    private ArrayList<PlantumlObject> getAbstractObjects(List<AstAbstractClafer> abstractClafers) {
        ArrayList<PlantumlObject> objs = new ArrayList<PlantumlObject>();

        for (AstAbstractClafer ast: abstractClafers) {
            ArrayList<PlantumlPropertyGroup> pgs = new ArrayList<PlantumlPropertyGroup>();

            ArrayList<PlantumlProperty> constrs = new ArrayList<PlantumlProperty>();
            for (AstConstraint constr: ast.getConstraints()) {
                constrs.add(new PlantumlProperty(constr.toString()));
            }

            if (constrs.size() > 0){
                pgs.add(new PlantumlPropertyGroup("Constraints", constrs.toArray(new PlantumlProperty[0])));
            }

            // create an object and add it
            PlantumlObject obj = new PlantumlObject(
                    SysmlCompilerUtils.getPropertyId(ast.getName()),
                    pgs.toArray(new PlantumlPropertyGroup[0])
            );
            objs.add(obj);

            // add all of its children
            // TODO: check for collisions?
            objs.addAll(getAbstractObjects(ast.getAbstractChildren()));
            objs.addAll(getConcreteObjects(ast.getChildren()));
        }
        return objs;
    }

    /**
     * top-level object collector
     * @param model the root clafer model
     * @return ArrayList of all clafers (abstract and concrete) suitable for PlantUML objects
     */
    private ArrayList<PlantumlObject> getObjects(AstModel model) {
        ArrayList<PlantumlObject> objs = getAbstractObjects(model.getAbstracts());
        objs.addAll(getConcreteObjects(model.getChildren()));
        return objs;
    }

    public PlantumlProgram compile(AstModel model) {
        ArrayList<PlantumlObject> objs = getObjects(model);

        return new PlantumlProgram(
           objs.toArray(new PlantumlObject[0]),
                new PlantumlConnection[0]
        );
    }
}
