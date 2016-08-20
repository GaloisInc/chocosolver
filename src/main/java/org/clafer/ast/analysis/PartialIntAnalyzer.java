package org.clafer.ast.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.clafer.ast.AstAbstractClafer;
import org.clafer.ast.AstArithm;
import org.clafer.ast.AstBoolArithm;
import org.clafer.ast.AstBoolExpr;
import org.clafer.ast.AstChildRelation;
import org.clafer.ast.AstClafer;
import org.clafer.ast.AstConcreteClafer;
import org.clafer.ast.AstConstant;
import org.clafer.ast.AstConstraint;
import org.clafer.ast.AstGlobal;
import org.clafer.ast.AstJoin;
import org.clafer.ast.AstJoinRef;
import org.clafer.ast.AstRef;
import org.clafer.ast.AstSetExpr;
import org.clafer.ast.AstSetTest;
import org.clafer.ast.AstTernary;
import org.clafer.ast.AstThis;
import org.clafer.ast.AstUnion;
import org.clafer.ast.AstUpcast;
import org.clafer.ast.AstUtil;
import org.clafer.collection.Either;
import org.clafer.collection.Pair;
import org.clafer.common.Util;
import org.clafer.domain.Domain;
import org.clafer.domain.Domains;
import org.clafer.ontology.Concept;
import org.clafer.ontology.KnowledgeDatabase;
import org.clafer.ontology.Oracle;
import org.clafer.ontology.Path;

/**
 *
 * @author jimmy
 */
public class PartialIntAnalyzer {

    private final Analysis analysis;
    private final Map<AstClafer, Concept> conceptMap = new HashMap<>();
    private final Map<Concept, AstClafer> claferMap = new HashMap<>();
    private final KnowledgeDatabase knowledgeDatabase = new KnowledgeDatabase();

    private PartialIntAnalyzer(Analysis analysis) {
        this.analysis = analysis;
    }

    private Concept asConcept(AstClafer clafer) {
        Concept concept = conceptMap.get(clafer);
        if (concept == null) {
            concept = knowledgeDatabase.newConcept(clafer.getName());
            conceptMap.put(clafer, concept);
            claferMap.put(concept, clafer);
        }
        return concept;
    }

    private AstClafer asClafer(Concept concept) {
        return claferMap.get(concept);
    }

    public static Analysis analyze(Analysis analysis) {
        PartialIntAnalyzer analyzer = new PartialIntAnalyzer(analysis);

        analysis.getClafers().forEach(analyzer::analyzeClafer);

        for (Entry<AstConstraint, AstBoolExpr> constraintExpr : analysis.getConstraintExprs().entrySet()) {
            AstConstraint constraint = constraintExpr.getKey();
            AstBoolExpr expr = constraintExpr.getValue();

            if (constraint.isHard()) {
                analyzer.analyzeConstraint(expr, constraint.getContext(), null);
            }
        }

        Oracle oracle = analyzer.knowledgeDatabase.oracle();
        do {
            for (Entry<AstConstraint, AstBoolExpr> constraintExpr : analysis.getConstraintExprs().entrySet()) {
                AstConstraint constraint = constraintExpr.getKey();
                AstBoolExpr expr = constraintExpr.getValue();

                if (constraint.isHard()) {
                    analyzer.analyzeConstraint(expr, constraint.getContext(), oracle);
                }
            }
        } while (oracle.propagate());

        Map<AstRef, Domain[]> partialInts = analyzer.partialInts(oracle);
        return analysis.setPartialIntsMap(partialInts);
    }

    /**
     * Returns a map from Clafer to a set of covering paths reaching that
     * Clafer.
     *
     * Each Clafer maps to a two dimensional array of Paths. The first dimension
     * is for the id of the Clafer. The second dimension contains the set of
     * covering paths for that (Clafer, id) pair.
     *
     * Uses dynamic programming, which works well unless of the hierarchy
     * contains circularity.
     *
     * @return a map from Clafer to a set of covering paths reaching that Clafer
     */
    private Map<AstClafer, Path[][]> pathsToClafers() {
        Map<AstClafer, Path[][]> pathsToClafers = new HashMap<>(analysis.getClafers().size());

        // For each potential (Clafer, id) pair, find all paths that can reach it.
        for (Set<AstClafer> component : analysis.getClafersInParentAndSubOrder()) {
            for (AstClafer clafer : component) {
                PartialSolution partialSolution = analysis.getPartialSolution(clafer);
                Path[][] paths = new Path[partialSolution.size()][];
                if (clafer instanceof AstAbstractClafer) {
                    AstAbstractClafer abstractClafer = (AstAbstractClafer) clafer;
                    Offsets offsets = analysis.getOffsets(abstractClafer);
                    for (AstClafer sub : abstractClafer.getSubs()) {
                        int offset = offsets.getOffset(sub);
                        Path[][] subPaths = pathsToClafers.get(sub);
                        if (subPaths == null) {
                            // This can occur if circularity in hierarcy.
                            Arrays.fill(paths, new Path[]{new Path(asConcept(clafer))});
                            break;
                        }
                        System.arraycopy(subPaths, 0, paths, offset, subPaths.length);
                    }
                } else {
                    if (clafer.hasParent()) {
                        Concept concept = asConcept(clafer);
                        Path[][] parentPaths = pathsToClafers.get(clafer.getParent());
                        if (parentPaths == null) {
                            // This can occur if circularity in hierarcy.
                            Arrays.fill(paths, new Path[]{new Path(asConcept(clafer))});
                        } else {
                            Path[][] childPaths = new Path[parentPaths.length][];
                            for (int i = 0; i < childPaths.length; i++) {
                                childPaths[i] = new Path[parentPaths[i].length];
                                for (int j = 0; j < childPaths[i].length; j++) {
                                    childPaths[i][j] = parentPaths[i][j].append(concept);
                                }
                            }
                            for (int i = 0; i < paths.length; i++) {
                                int[] possibleParents = partialSolution.getPossibleParents(i);
                                int size = 0;
                                for (int possibleParent : possibleParents) {
                                    size += childPaths[possibleParent].length;
                                }
                                Path[] pathsForId = new Path[size];
                                int offset = 0;
                                for (int possibleParent : possibleParents) {
                                    System.arraycopy(
                                            childPaths[possibleParent], 0,
                                            pathsForId, offset,
                                            childPaths[possibleParent].length);
                                    offset += childPaths[possibleParent].length;
                                }
                                paths[i] = pathsForId;
                            }
                        }
                    } else {
                        Arrays.fill(paths, new Path[]{new Path(asConcept(clafer))});
                    }
                }
                pathsToClafers.put(clafer, paths);
            }
        }
        return pathsToClafers;
    }

    private Map<AstRef, Domain[]> partialInts(Oracle oracle) {
        Map<AstClafer, Path[][]> pathsToClafers = pathsToClafers();

        Map<AstRef, Domain[]> partialInts = new HashMap<>();
        for (AstClafer clafer : analysis.getClafers()) {
            if (clafer.hasRef()) {
                Path[][] paths = pathsToClafers.get(clafer);
                Domain[] domains = new Domain[paths.length];
                for (int i = 0; i < paths.length; i++) {
                    Domain domain = Domains.EmptyDomain;
                    for (Path path : paths[i]) {
                        Domain pathDomain = oracle.getAssignment(path);
                        if (pathDomain == null) {
                            domain = null;
                            break;
                        }
                        domain = domain.union(pathDomain);
                    }
                    domains[i] = domain;
                }
                partialInts.put(clafer.getRef(), domains);
            }
        }
        return partialInts;
    }

    private void analyzeClafer(AstClafer clafer) {
        if (clafer.hasSuperClafer()) {
            knowledgeDatabase.newIsA(asConcept(clafer), asConcept(clafer.getSuperClafer()));
        }
        clafer.getChildren().forEach(child
                -> knowledgeDatabase.newHasA(asConcept(clafer), asConcept(child)));
    }

    private void analyzeConstraint(AstBoolExpr expr, AstClafer context, Oracle oracle) {
        analyzeExpr(expr, context, oracle).forEach(x -> addEquality(x.getFst(), x.getSnd(), context, oracle));
    }

    private void addEquality(Path var, Either<Domain, Path> value, AstClafer context, Oracle oracle) {
        if (value.isLeft()) {
            if (var instanceof LocalPath || analysis.getGlobalCard(context).getLow() > 0) {
                if (oracle == null) {
                    knowledgeDatabase.newAssignment(var, value.getLeft());
                } else {
                    oracle.newAssignment(var, value.getLeft());
                }
            }
        } else {
            if (var instanceof LocalPath && value.getRight() instanceof LocalPath) {
                knowledgeDatabase.newLocalEquality(var, value.getRight());
            } else if (var instanceof LocalPath || value.getRight() instanceof LocalPath) {
                Path localPath = var instanceof LocalPath ? var : value.getRight();
                Path globalPath = var instanceof LocalPath ? value.getRight() : var;
                for (AstConcreteClafer sub : AstUtil.getConcreteSubs(asClafer(localPath.getContext()))) {
                    if (analysis.getGlobalCard(sub).getLow() > 0) {
                        knowledgeDatabase.newLocalEquality(
                                localPath.replaceContext(asConcept(sub)),
                                globalPath);
                    }
                }
            } else {
                if (analysis.getGlobalCard(context).getLow() > 0) {
                    knowledgeDatabase.newLocalEquality(var, value.getRight());
                }
            }
        }
    }

    private List<Pair<Path, Either<Domain, Path>>> analyzeExpr(
            AstBoolExpr expr, AstClafer context, Oracle oracle) {
        if (expr instanceof AstSetTest) {
            AstSetTest compare = (AstSetTest) expr;
            switch (compare.getOp()) {
                case Equal:
                    List<Pair<Path, Either<Domain, Path>>> paths = new ArrayList<>(2);
                    if (compare.getLeft() instanceof AstJoinRef) {
                        analyzeEqual((AstJoinRef) compare.getLeft(), compare.getRight(), context, oracle)
                                .ifPresent(paths::add);
                    }
                    if (compare.getRight() instanceof AstJoinRef) {
                        analyzeEqual((AstJoinRef) compare.getRight(), compare.getLeft(), context, oracle)
                                .ifPresent(paths::add);
                    }
                    return paths;
            }
        } else if (expr instanceof AstBoolArithm) {
            if (expr instanceof AstBoolArithm) {
                AstBoolArithm boolArithm = (AstBoolArithm) expr;
                switch (boolArithm.getOp()) {
                    case And:
                        List<Pair<Path, Either<Domain, Path>>> paths = new ArrayList<>();
                        for (AstBoolExpr operand : boolArithm.getOperands()) {
                            paths.addAll(analyzeExpr(operand, context, oracle));
                        }
                        return paths;
                    case Or:
//                        AstBoolExpr[] operands = boolArithm.getOperands();
//                        List<Pair<Path, Domain>> paths = analyzeExpr(operands[0], context);
//                        for (int i = 1; i < operands.length && !paths.isEmpty(); i++) {
//                            List<Pair<Path, Domain>> disjunctionPaths = analyzeExpr(operands[i], context);
//                            paths.removeIf(x -> disjunctionPaths.stream().map(Pair::getFst).noneMatch(x::equals));
//                            disjunctionPaths.removeIf(x -> paths.stream().map(Pair::getFst).noneMatch(x::equals));
//
//                            paths.addAll(disjunctionPaths);
//                        }
//                        return paths;
                }
            }
        }
        return Collections.emptyList();
    }

    private Optional<Pair<Path, Either<Domain, Path>>> analyzeEqual(
            AstJoinRef var, AstSetExpr value, AstClafer context, Oracle oracle) {
        Optional<Path> varPath = asPath(var.getDeref(), context);
        if (varPath.isPresent()) {
            Optional<Domain> valueDomain = asDomain(value, context, oracle);
            if (valueDomain.isPresent()) {
                return Optional.of(new Pair<>(varPath.get(), Either.left(valueDomain.get())));
            }
            if (value instanceof AstJoinRef) {
                Optional<Path> valuePath = asPath((AstJoinRef) value, context);
                if (valuePath.isPresent()) {
                    return Optional.of(new Pair<>(varPath.get(), Either.right(valuePath.get())));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Path> asPath(AstJoinRef ast, AstClafer context) {
        return asPath(ast.getDeref(), context);
    }

    private Optional<Path> asPath(AstSetExpr ast, AstClafer context) {
        if (ast instanceof AstThis) {
            return asPath((AstThis) ast, context);
        }
        if (ast instanceof AstGlobal) {
            return asPath((AstGlobal) ast, context);
        }
        if (ast instanceof AstConstant) {
            return asPath((AstConstant) ast, context);
        }
        if (ast instanceof AstJoin) {
            return asPath((AstJoin) ast, context);
        }
        if (ast instanceof AstUpcast) {
            return asPath((AstUpcast) ast, context);
        }
        return Optional.empty();
    }

    private Optional<Path> asPath(AstUpcast ast, AstClafer context) {
        return asPath(ast.getBase(), context);
    }

    private Optional<Path> asPath(AstJoin ast, AstClafer context) {
        if (ast.getRight() instanceof AstChildRelation) {
            AstChildRelation right = (AstChildRelation) ast.getRight();

            Optional<Path> left = asPath(ast.getLeft(), context);
            return left.map(x -> x.append(asConcept(right.getChildType())));
        }
        return Optional.empty();
    }

    private Optional<Path> asPath(AstThis ast, AstClafer context) {
        return Optional.of(new LocalPath(asConcept(context)));
    }

    private Optional<Path> asPath(AstGlobal ast, AstClafer context) {
        return Optional.of(new Path(asConcept(ast.getType().getParent()), asConcept(ast.getType())));
    }

    private Optional<Path> asPath(AstConstant ast, AstClafer context) {
        if (ast.getType().isClaferType()) {
            return Optional.of(new Path(asConcept(ast.getType().getClaferType())));
        }
        return Optional.empty();
    }

    private Optional<Domain> asDomain(AstSetExpr ast, AstClafer context, Oracle oracle) {
        if (ast instanceof AstConstant) {
            return asDomain((AstConstant) ast, context, oracle);
        }
        if (ast instanceof AstArithm) {
            return asDomain((AstArithm) ast, context, oracle);
        }
        if (ast instanceof AstUnion) {
            return asDomain((AstUnion) ast, context, oracle);
        }
        if (ast instanceof AstTernary) {
            return asDomain((AstTernary) ast, context, oracle);
        }
        if (ast instanceof AstUpcast) {
            return asDomain((AstUpcast) ast, context, oracle);
        }
        if (oracle != null && ast instanceof AstJoinRef) {
            AstJoinRef joinRef = (AstJoinRef) ast;
            Optional<Path> path = asPath(joinRef.getDeref(), context);
            return path.map(oracle::getAssignment);
        }
        return Optional.empty();
    }

    private Optional<Domain> asDomain(AstConstant ast, AstClafer context, Oracle oracle) {
        if (ast.getValue().length > 0 && ast.getValue()[0].length == 1) {
            int[] values = new int[ast.getValue().length];
            for (int i = 0; i < values.length; i++) {
                values[i] = ast.getValue()[i][0];
            }
            return Optional.of(Domains.enumDomain(values));
        }
        return Optional.empty();
    }

    private Optional<Domain> asDomain(AstArithm ast, AstClafer context, Oracle oracle) {
        int[] operands = new int[ast.getOperands().length];
        for (int i = 0; i < operands.length; i++) {
            Optional<Domain> domain = asDomain(ast.getOperands()[i], context, oracle);
            if (!domain.isPresent() || domain.get().size() != 1) {
                return Optional.empty();
            }
            operands[i] = domain.get().getLowBound();
        }
        switch (ast.getOp()) {
            case Add:
                return Optional.of(Domains.constantDomain(Util.sum(operands)));
            case Sub:
                int difference = operands[0];
                for (int i = 1; i < operands.length; i++) {
                    difference -= operands[i];
                }
                return Optional.of(Domains.constantDomain(difference));
            case Mul:
                int product = 1;
                for (int operand : operands) {
                    product *= operand;
                }
                return Optional.of(Domains.constantDomain(product));
            case Div:
                int quotient = operands[0];
                for (int i = 1; i < operands.length; i++) {
                    quotient /= operands[i];
                }
                return Optional.of(Domains.constantDomain(quotient));
            default:
                return Optional.empty();
        }
    }

    private Optional<Domain> asDomain(AstUnion ast, AstClafer context, Oracle oracle) {
        Optional<Domain> left = asDomain(ast.getLeft(), context, oracle);
        if (left.isPresent()) {
            Optional<Domain> right = asDomain(ast.getRight(), context, oracle);
            if (right.isPresent()) {
                return Optional.of(left.get().union(right.get()));
            }
        }
        return Optional.empty();
    }

    private Optional<Domain> asDomain(AstTernary ast, AstClafer context, Oracle oracle) {
        Optional<Domain> consequent = asDomain(ast.getConsequent(), context, oracle);
        if (consequent.isPresent()) {
            Optional<Domain> alternative = asDomain(ast.getAlternative(), context, oracle);
            if (alternative.isPresent()) {
                return Optional.of(consequent.get().union(alternative.get()));
            }
        }
        return Optional.empty();
    }

    private Optional<Domain> asDomain(AstUpcast ast, AstClafer context, Oracle oracle) {
        Optional<Domain> base = asDomain(ast.getBase(), context, oracle);
        if (base.isPresent()) {
            int offset = analysis.getOffset(
                    ast.getTarget().getClaferType(),
                    analysis.getType(ast.getBase()).getClaferType());
            return Optional.of(base.get().offset(offset));
        }
        return Optional.empty();
    }

    private class LocalPath extends Path {

        public LocalPath(Concept... steps) {
            super(steps);
        }

        @Override
        protected Path newPath(Concept... steps) {
            return new LocalPath(steps);
        }
    }
}
