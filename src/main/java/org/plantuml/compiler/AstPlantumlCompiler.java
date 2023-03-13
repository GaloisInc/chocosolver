package org.plantuml.compiler;

import org.clafer.ast.*;
import org.plantuml.ast.*;
import org.sysml.compiler.SysmlCompilerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Clafer AST to PlantUML
 *
 * Note that this compilation doesn't require instances, so we don't need to run the solver
 * to compile.
 *
 * TODO: this should be refactored to cut down the code re-use.
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
            if (ast.getRef() != null) {
                continue;
            }
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

            if (!obj.getName().startsWith("#")) {
                objs.add(obj);
            }

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
            if (ast.getRef() != null) {
                continue;
            }
            ArrayList<PlantumlPropertyGroup> pgs = new ArrayList<PlantumlPropertyGroup>();

            ArrayList<PlantumlProperty> constrs = new ArrayList<PlantumlProperty>();
            for (AstConstraint constr: ast.getConstraints()) {
                constrs.add(new PlantumlProperty(constr.toString()));
            }

            ArrayList<PlantumlProperty> refs = new ArrayList<PlantumlProperty>();
            for (AstConcreteClafer clafer: ast.getChildren()){
                AstRef ref = clafer.getRef();
                if (ref != null) {
                    refs.add(new PlantumlProperty(ref.toString()));
                }
            }

            if (refs.size() > 0){
                pgs.add(new PlantumlPropertyGroup("Attributes", refs.toArray(new PlantumlProperty[0])));
            }

            if (constrs.size() > 0){
                pgs.add(new PlantumlPropertyGroup("Constraints", constrs.toArray(new PlantumlProperty[0])));
            }

            // create an object and add it
            PlantumlObject obj = new PlantumlObject(
                    SysmlCompilerUtils.getPropertyId(ast.getName()),
                    pgs.toArray(new PlantumlPropertyGroup[0])
            );

            if (!obj.getName().startsWith("#")){
                objs.add(obj);
            }

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

    private ArrayList<PlantumlConnection> getConcreteConnections(List<AstConcreteClafer> concreteClafers) {
        ArrayList<PlantumlConnection> connections = new ArrayList<PlantumlConnection>();

        for (AstConcreteClafer ast: concreteClafers) {
            if (ast.getRef() != null) {
                continue;
            }
            String fromObj = SysmlCompilerUtils.getPropertyId(ast.getParent().getName());
            String toObj = SysmlCompilerUtils.getPropertyId(ast.getName());
            Card card = ast.getCard();
            String label = "";
            char toConn = '*';
            char fromConn = '-';
            if (ast.getParent().hasGroupCard()){
                if (ast.getParent().getGroupCard().toString().equals("1")){
                    fromConn = '+';
                } else if (ast.getParent().getGroupCard().toString().equals("1..*")) {
                    fromConn = '*';
                }
            }
            if (card.toString().equals("0..1")){
                toConn = 'o';
            } else if (card.toString().equals("1")) {
               toConn = '*';
            } else {
                if (card.toString().startsWith("0")) {
                    toConn = 'o';
                }
                label = card.toString();
            }
            if (!(fromObj.startsWith("#") || toObj.startsWith("#"))) {
                connections.add(
                        new PlantumlConnection(
                                fromObj,
                                toObj,
                                fromConn,
                                toConn,
                                label
                        )
                );
            }

            AstClafer superClafer = ast.getSuperClafer();
            if (superClafer != null) {
                String scName = SysmlCompilerUtils.getPropertyId(superClafer.getName());
                if (!scName.startsWith("#")) {
                    fromObj = toObj;
                    toObj = scName;
                    connections.add(
                            new PlantumlConnection(
                                    fromObj,
                                    toObj,
                                    '.',
                                    '>',
                                    "",
                                    '.'
                            )
                    );
                }
            }

            connections.addAll(getConcreteConnections(ast.getChildren()));
        }

        return connections;
    }

    private ArrayList<PlantumlConnection> getAbstractConnections(List<AstAbstractClafer> abstractClafers) {
        ArrayList<PlantumlConnection> connections = new ArrayList<PlantumlConnection>();

        for (AstAbstractClafer ast: abstractClafers) {
            if (ast.getRef() != null) {
                continue;
            }
            String fromObj = SysmlCompilerUtils.getPropertyId(ast.getParent().getName());
            String toObj = SysmlCompilerUtils.getPropertyId(ast.getName());
            String label = "";
            char toConn = '*';
            char fromConn = '-';
            if (ast.getParent().hasGroupCard()){
                if (ast.getParent().getGroupCard().toString().equals("1")){
                    fromConn = '+';
                } else if (ast.getParent().getGroupCard().toString().equals("1..*")) {
                    fromConn = '*';
                }
            }
            if (!(fromObj.startsWith("#") || toObj.startsWith("#"))) {
                connections.add(
                        new PlantumlConnection(
                                fromObj,
                                toObj,
                                fromConn,
                                toConn,
                                label
                        )
                );
            }

            AstClafer superClafer = ast.getSuperClafer();
            if (superClafer != null) {
                String scName = SysmlCompilerUtils.getPropertyId(superClafer.getName());
                if (!scName.startsWith("#")) {
                    fromObj = toObj;
                    toObj = scName;
                    connections.add(
                            new PlantumlConnection(
                                    fromObj,
                                    toObj,
                                    '.',
                                    '>',
                                    "",
                                    '.'
                            )
                    );
                }
            }

            connections.addAll(getAbstractConnections(ast.getAbstractChildren()));
            connections.addAll(getConcreteConnections(ast.getChildren()));
        }

        return connections;
    }

    private ArrayList<PlantumlConnection> getConnections(AstModel model) {
        ArrayList<PlantumlConnection> connections = getAbstractConnections(model.getAbstracts());
        connections.addAll(getConcreteConnections(model.getChildren()));
        return connections;
    }

    public PlantumlProgram compile(AstModel model) {
        ArrayList<PlantumlObject> objs = getObjects(model);
        ArrayList<PlantumlConnection> conns = getConnections(model);

        return new PlantumlProgram(
           objs.toArray(new PlantumlObject[0]), conns.toArray(new PlantumlConnection[0])
        );
    }
}
