package org.plantuml.compiler;

import org.clafer.ast.*;
import org.plantuml.ast.*;
import org.sysml.compiler.SysmlCompilerUtils;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Clafer AST to PlantUML
 *
 * Note that this compilation doesn't require instances, so we don't need to run the solver
 * to compile.
 *
 * TODO: this should be refactored to cut down the code re-use.
 */
public class AstPlantumlCompiler {
    private final boolean includeConstraints;
    private final boolean includeSuperClafers;
    private final int includeLevels;

    public AstPlantumlCompiler(AstPlantumlCompilerBuilder builder){
        this.includeConstraints = builder.includeConstraints;
        this.includeSuperClafers = builder.includeSuperClafers;
        this.includeLevels = builder.includeLevels;
    }

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
            if (constrs.size() > 0 && this.includeConstraints){
                pgs.add(new PlantumlPropertyGroup("Constraints", constrs.toArray(new PlantumlProperty[0])));
            }

            // Display inheritance as a style notation
            // NOTE: this could be made optional
            AstClafer superClafer = ast.getSuperClafer();
            String scName = "";
            if (superClafer != null & !SysmlCompilerUtils.getPropertyId(superClafer.getName()).startsWith("#")) {
                scName = " <<" + SysmlCompilerUtils.getPropertyId(superClafer.getName()) + ">>";
            }

            // create an object and add it
            PlantumlObject obj = new PlantumlObject(
                    SysmlCompilerUtils.getPropertyId(ast.getName() + scName),
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

            if (constrs.size() > 0 && this.includeConstraints){
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
            // NOTE: this is pretty ugly
            if (ast.getParent().hasGroupCard()){
                if (ast.getParent().getGroupCard().toString().equals("1")){
                    // This is an OR (exactly one)
                    fromConn = '+';
                } else if (ast.getParent().getGroupCard().toString().equals("1..*")) {
                    // This is an Alternative (1 or more)
                    fromConn = ')';
                }
                // Common setting
                toConn = '-';
                if (!card.toString().equals("0..1")){
                    label = card.toString();
                }
            } else {
                // No Alternative/OR
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

            if (this.includeSuperClafers) {
                AstClafer superClafer = ast.getSuperClafer();
                if (superClafer != null) {
                    String scName = SysmlCompilerUtils.getPropertyId(superClafer.getName());
                    // NOTE: a little hack to ignore the basic abstract clafers
                    // this should be configurable
                    if (!scName.startsWith("#") & (!scName.equals("PowerFeature")) & (!scName.equals("WaveformFeature"))) {
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

            if (this.includeSuperClafers) {
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


    /**
     * builder class to configure the Compiler
     * we anticipate a lot of options here, so we use a builder
     */
    public static class AstPlantumlCompilerBuilder{

        private boolean includeConstraints;
        private boolean includeSuperClafers;
        private int includeLevels;

        public AstPlantumlCompilerBuilder(){
            this.includeConstraints = true;
            this.includeSuperClafers = true;
            this.includeLevels = -1;
        }

        public AstPlantumlCompiler build(){
            return new AstPlantumlCompiler(this);
        }

        public AstPlantumlCompilerBuilder setIncludeConstraints(boolean includeConstraints){
            this.includeConstraints = includeConstraints;
            return this;
        }

        public AstPlantumlCompilerBuilder setIncludeSuperClafers(boolean includeSuperClafers){
            this.includeSuperClafers = includeSuperClafers;
            return this;
        }

        public AstPlantumlCompilerBuilder setLevels(int levels){
            this.includeLevels = levels;
            return this;
        }

        /**
         * read builder from an input toml file (null returns defaults)
         * @param tomlFile config file
         * @return a compiler object
         * @throws IOException
         */
        static public AstPlantumlCompiler buildFromToml(File tomlFile) throws IOException {
            if (tomlFile == null) {
                return new AstPlantumlCompiler(new AstPlantumlCompilerBuilder());
            } else {
                // TODO: Toml4J seems to do better error checking than this
                Path source = Paths.get(tomlFile.toURI());
                TomlParseResult result = Toml.parse(source);
                result.errors().forEach(error -> System.err.println(error.toString()));

                AstPlantumlCompilerBuilder build = new AstPlantumlCompilerBuilder();

                String field = "include.super_clafers";
                if (result.contains(field)) build.setIncludeSuperClafers(Boolean.TRUE.equals(result.getBoolean(field)));

                field = "include.constraints";
                if (result.contains(field)) build.setIncludeConstraints(Boolean.TRUE.equals(result.getBoolean(field)));

                field = "include.levels";
                if (result.contains(field)) build.setLevels(Objects.requireNonNull(result.getLong(field)).intValue());

                return build.build();
            }
        }
    }
}
