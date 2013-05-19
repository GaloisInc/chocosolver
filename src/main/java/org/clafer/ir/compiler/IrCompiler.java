package org.clafer.ir.compiler;

import org.clafer.collection.CacheMap;
import org.clafer.ir.IrAllDifferent;
import org.clafer.ir.IrArithm;
import org.clafer.ir.IrArrayToSet;
import org.clafer.ir.IrBoolCast;
import org.clafer.ir.IrElement;
import org.clafer.ir.IrIfOnlyIf;
import org.clafer.ir.IrIfThenElse;
import org.clafer.ir.IrIntExpr;
import org.clafer.ir.IrBetween;
import org.clafer.ir.IrMember;
import org.clafer.ir.IrNotBetween;
import org.clafer.ir.IrNotImplies;
import org.clafer.ir.IrNotMember;
import org.clafer.ir.IrOr;
import org.clafer.ir.IrSelectN;
import org.clafer.ir.IrSetExpr;
import gnu.trove.set.hash.TIntHashSet;
import java.util.Deque;
import java.util.LinkedList;
import org.clafer.ir.IrNot;
import org.clafer.ir.IrSetEquality;
import org.clafer.ir.IrSingleton;
import org.clafer.ir.IrSortInts;
import org.clafer.ir.IrSortStrings;
import org.clafer.ir.IrUnion;
import solver.constraints.nary.cnf.ConjunctiveNormalForm;
import org.clafer.ir.IrAnd;
import org.clafer.common.Check;
import org.clafer.common.Util;
import org.clafer.choco.constraint.Constraints;
import org.clafer.ir.IrBoolChannel;
import org.clafer.ir.IrIntChannel;
import org.clafer.ir.IrJoin;
import org.clafer.ir.IrJoinRef;
import org.clafer.ir.IrCard;
import solver.variables.SetVar;
import solver.constraints.Constraint;
import org.clafer.ir.IrBoolExpr;
import org.clafer.ir.IrBoolExprVisitor;
import org.clafer.ir.IrBoolLiteral;
import org.clafer.ir.IrBoolVar;
import org.clafer.ir.IrDomain;
import org.clafer.ir.IrException;
import org.clafer.ir.IrImplies;
import org.clafer.ir.IrCompare;
import org.clafer.ir.IrIntExprVisitor;
import org.clafer.ir.IrIntLiteral;
import org.clafer.ir.IrIntVar;
import org.clafer.ir.IrModule;
import org.clafer.ir.IrSetExprVisitor;
import org.clafer.ir.IrSetLiteral;
import org.clafer.ir.IrSetVar;
import org.clafer.ir.IrUtil;
import org.clafer.ir.analysis.ExpressionAnalysis;
import solver.Solver;
import solver.constraints.IntConstraintFactory;
import solver.constraints.LogicalConstraintFactory;
import solver.constraints.nary.Sum;
import solver.constraints.nary.cnf.LogOp;
import solver.constraints.set.SetConstraintsFactory;
import solver.variables.BoolVar;
import solver.variables.IntVar;
import solver.variables.VariableFactory;

/**
 * Compile from IR to Choco.
 *
 * @author jimmy
 */
public class IrCompiler {

    private final Solver solver;
    private int varNum = 0;

    private IrCompiler(Solver solver) {
        this.solver = Check.notNull(solver);
    }

    public static IrSolutionMap compile(IrModule in, Solver out) {
        IrCompiler compiler = new IrCompiler(out);
        return compiler.compile(in);
    }

    private IrSolutionMap compile(IrModule optModule) {
        optModule = ExpressionAnalysis.analyze(optModule);
        for (IrBoolVar var : optModule.getBoolVars()) {
            boolVar.get(var);
        }
        for (IrIntVar var : optModule.getIntVars()) {
            intVar.get(var);
        }
        for (IrSetVar var : optModule.getSetVars()) {
            setVar.get(var);
        }
        for (IrBoolExpr constraint : optModule.getConstraints()) {
            solver.post(compileAsConstraint(constraint));
        }
        return new IrSolutionMap(boolVar, intVar, setVar);
    }

    private BoolVar numBoolVar(String name) {
        return VariableFactory.bool(name + "#" + varNum++, solver);
    }

    private IntVar intVar(String name, IrDomain domain) {
        if (domain.getLowerBound() == 0 && domain.getUpperBound() == 1) {
            return VariableFactory.bool(name, solver);
        }
        if (domain.isBounded()) {
            return VariableFactory.enumerated(name, domain.getLowerBound(), domain.getUpperBound(), solver);
        }
        return VariableFactory.enumerated(name, domain.getValues(), solver);
    }

    private IntVar numIntVar(String name, IrDomain domain) {
        return intVar(name + "#" + varNum++, domain);
    }

    private IntVar numIntVar(String name, int low, int high) {
        return VariableFactory.enumerated(name + "#" + varNum++, low, high, solver);
    }

    private IntVar numIntVar(String name, int[] dom) {
        return VariableFactory.enumerated(name + "#" + varNum++, dom, solver);
    }

    private SetVar numSetVar(String name, IrDomain env, IrDomain ker) {
        return VariableFactory.set(name + "#" + varNum++, env.getValues(), ker.getValues(), solver);
    }

    private final CacheMap<IrBoolVar, BoolVar> boolVar = new CacheMap<IrBoolVar, BoolVar>() {
        @Override
        protected BoolVar cache(IrBoolVar ir) {
            Boolean constant = IrUtil.getConstant(ir);
            if (constant != null) {
                return constant.booleanValue() ? VariableFactory.one(solver) : VariableFactory.zero(solver);
            }
            return VariableFactory.bool(ir.getName(), solver);
        }
    };
    private final CacheMap<IrIntVar, IntVar> intVar = new CacheMap<IrIntVar, IntVar>() {
        @Override
        protected IntVar cache(IrIntVar ir) {
            Integer constant = IrUtil.getConstant(ir);
            if (constant != null) {
                switch (constant.intValue()) {
                    case 0:
                        return VariableFactory.zero(solver);
                    case 1:
                        return VariableFactory.one(solver);
                    default:
                        return VariableFactory.fixed(constant, solver);
                }
            }
            return intVar(ir.getName(), ir.getDomain());
        }
    };
    private final CacheMap<IrSetVar, SetVar> setVar = new CacheMap<IrSetVar, SetVar>() {
        @Override
        protected SetVar cache(IrSetVar a) {
            int[] constant = IrUtil.getConstant(a);
            if (constant != null) {
                return VariableFactory.set(a.toString(), constant, constant, solver);
            }
            IrDomain env = a.getEnv();
            IrDomain ker = a.getKer();
            IrDomain card = a.getCard();
            SetVar set = VariableFactory.set(a.getName(), env.getValues(), ker.getValues(), solver);

            if (card.getUpperBound() < env.size()) {
                IntVar setCard = setCardVar.get(set);
                solver.post(_arithm(setCard, "<=", card.getUpperBound()));
            }
            if (card.getLowerBound() > ker.size()) {
                IntVar setCard = setCardVar.get(set);
                solver.post(_arithm(setCard, ">=", card.getLowerBound()));
            }

            return set;
        }
    };
    private final CacheMap<SetVar, IntVar> setCardVar = new CacheMap<SetVar, IntVar>() {
        @Override
        protected IntVar cache(SetVar a) {
            IntVar card = VariableFactory.enumerated("|" + a.getName() + "|", a.getKernelSize(), a.getEnvelopeSize(), solver);
            solver.post(SetConstraintsFactory.cardinality(a, card));
            return card;
        }
    };

    private BoolVar asBoolVar(Object obj) {
        if (obj instanceof Constraint) {
            return asBoolVar((Constraint) obj);
        }
        return (BoolVar) obj;
    }

    private BoolVar asBoolVar(Constraint op) {
        return op.reif();
    }

    private BoolVar compileAsBoolVar(IrBoolExpr expr) {
        return asBoolVar(expr.accept(boolExprCompiler1, Preference.BoolVar));
    }

    private Constraint asConstraint(Object obj) {
        if (obj instanceof BoolVar) {
            return asConstraint((BoolVar) obj);
        }
        return (Constraint) obj;
    }

    private Constraint asConstraint(BoolVar var) {
        return _arithm(var, "=", 1);
    }

    private Constraint compileAsConstraint(IrBoolExpr expr) {
        return asConstraint(expr.accept(boolExprCompiler1, Preference.Constraint));
    }

    private IntVar compile(IrIntExpr expr) {
        return expr.accept(intExprCompiler, null);
    }

    private SetVar compile(IrSetExpr expr) {
        return expr.accept(setExprCompiler, null);
    }
    private final IrBoolExprVisitor<Preference, Object> boolExprCompiler1 = new IrBoolExprVisitor<Preference, Object>() {
        @Override
        public Object visit(IrBoolLiteral ir, Preference a) {
            return boolVar.get(ir.getVar());
        }

        @Override
        public Object visit(IrNot ir, Preference a) {
            return compileAsBoolVar(ir.getExpr()).not();
        }

        @Override
        public Object visit(IrAnd ir, Preference a) {
            IrBoolExpr[] operands = ir.getOperands();
            if (operands.length == 1) {
                return operands[0].accept(this, a);
            }
            BoolVar[] $operands = new BoolVar[operands.length];
            for (int i = 0; i < $operands.length; i++) {
                $operands[i] = compileAsBoolVar(operands[i]);
            }
            return _and($operands);
        }

        @Override
        public Object visit(IrOr ir, Preference a) {
            IrBoolExpr[] operands = ir.getOperands();
            if (operands.length == 1) {
                return operands[0].accept(this, a);
            }
            BoolVar[] $operands = new BoolVar[operands.length];
            for (int i = 0; i < $operands.length; i++) {
                $operands[i] = compileAsBoolVar(operands[i]);
            }
            return _or($operands);
        }

        @Override
        public Object visit(IrImplies ir, Preference a) {
            BoolVar $antecedent = compileAsBoolVar(ir.getAntecedent());
            BoolVar $consequent = compileAsBoolVar(ir.getConsequent());
            return _implies($antecedent, $consequent);
        }

        @Override
        public Object visit(IrNotImplies ir, Preference a) {
            BoolVar $antecedent = compileAsBoolVar(ir.getAntecedent());
            BoolVar $consequent = compileAsBoolVar(ir.getConsequent());
            return _not_implies($antecedent, $consequent);
        }

        @Override
        public Object visit(IrIfThenElse ir, Preference a) {
            BoolVar $antecedent = compileAsBoolVar(ir.getAntecedent());
            BoolVar $consequent = compileAsBoolVar(ir.getConsequent());
            BoolVar $alternative = compileAsBoolVar(ir.getAlternative());
            Constraint thenClause = _implies($antecedent, $consequent);
            Constraint elseClause = _implies($antecedent.not(), $alternative);
            return _and(thenClause.reif(), elseClause.reif());
        }

        @Override
        public Object visit(IrIfOnlyIf ir, Preference a) {
            BoolVar $left = compileAsBoolVar(ir.getLeft());
            BoolVar $right = compileAsBoolVar(ir.getRight());
            return _arithm($left, "=", $right);
        }

        @Override
        public Object visit(IrBetween ir, Preference a) {
            IntVar $var = ir.getVar().accept(intExprCompiler, null);
            return _between($var, ir.getLow(), ir.getHigh());
        }

        @Override
        public Object visit(IrNotBetween ir, Preference a) {
            IntVar $var = ir.getVar().accept(intExprCompiler, null);
            return _not_between($var, ir.getLow(), ir.getHigh());
        }

        @Override
        public Object visit(IrCompare ir, Preference a) {
            IntVar $left = ir.getLeft().accept(intExprCompiler, null);
            IntVar $right = ir.getRight().accept(intExprCompiler, null);
            return _arithm($left, ir.getOp().getSyntax(), $right);
        }

        @Override
        public Object visit(IrSetEquality ir, Preference a) {
            SetVar $left = ir.getLeft().accept(setExprCompiler, null);
            SetVar $right = ir.getRight().accept(setExprCompiler, null);
            switch (ir.getOp()) {
                case Equal:
                    return _equal($left, $right);
                case NotEqual:
                    return _all_different($left, $right);
                default:
                    throw new IrException();
            }
        }

        @Override
        public Object visit(IrMember ir, Preference a) {
            IntVar $element = ir.getElement().accept(intExprCompiler, null);
            SetVar $set = ir.getSet().accept(setExprCompiler, null);
            return _member($element, $set);
        }

        @Override
        public Object visit(IrNotMember ir, Preference a) {
            IntVar $element = ir.getElement().accept(intExprCompiler, null);
            SetVar $set = ir.getSet().accept(setExprCompiler, null);
            return _not_member($element, $set);
        }

        @Override
        public Object visit(IrBoolCast ir, Preference a) {
            BoolVar $expr = (BoolVar) ir.getExpr().accept(intExprCompiler, null);
            return ir.isFlipped() ? $expr.not() : $expr;
        }

        @Override
        public Constraint visit(IrBoolChannel ir, Preference a) {
            IrBoolExpr[] bools = ir.getBools();
            IrSetExpr set = ir.getSet();

            BoolVar[] $bools = new BoolVar[bools.length];
            for (int i = 0; i < $bools.length; i++) {
                $bools[i] = compileAsBoolVar(bools[i]);
            }
            SetVar $set = compile(set);
            return SetConstraintsFactory.bool_channel($bools, $set, 0);
        }

        @Override
        public Constraint visit(IrIntChannel ir, Preference a) {
            IrIntExpr[] ints = ir.getInts();
            IrSetExpr[] sets = ir.getSets();
            IntVar[] $ints = new IntVar[ints.length];
            for (int i = 0; i < $ints.length; i++) {
                $ints[i] = compile(ints[i]);
            }
            SetVar[] $sets = new SetVar[sets.length];
            for (int i = 0; i < $sets.length; i++) {
                $sets[i] = compile(sets[i]);
            }
            return Constraints.intChannel($sets, $ints);
        }

        @Override
        public Constraint visit(IrSortInts ir, Preference a) {
            IrIntExpr[] array = ir.getArray();
            IntVar[] $array = new IntVar[array.length];
            for (int i = 0; i < $array.length; i++) {
                $array[i] = compile(array[i]);
            }
            return Constraints.increasing($array);
        }

        @Override
        public Object visit(IrSortStrings ir, Preference a) {
            IrIntExpr[][] strings = ir.getStrings();
            IntVar[][] $strings = new IntVar[strings.length][];
            for (int i = 0; i < $strings.length; i++) {
                $strings[i] = new IntVar[strings[i].length];
                for (int j = 0; j < $strings[i].length; j++) {
                    $strings[i][j] = compile(strings[i][j]);
                }
            }
            return _lex_chain_less_eq($strings);
        }

        @Override
        public Constraint visit(IrAllDifferent ir, Preference a) {
            IrIntExpr[] operands = ir.getOperands();

            IntVar[] $operands = new IntVar[operands.length];
            for (int i = 0; i < $operands.length; i++) {
                $operands[i] = compile(operands[i]);
            }
            return _all_different($operands);
        }

        @Override
        public Constraint visit(IrSelectN ir, Preference a) {
            IrBoolExpr[] bools = ir.getBools();
            IrIntExpr n = ir.getN();
            BoolVar[] $bools = new BoolVar[bools.length];
            for (int i = 0; i < $bools.length; i++) {
                $bools[i] = compileAsBoolVar(bools[i]);
            }
            IntVar $n = compile(n);
            return Constraints.selectN($bools, $n);
        }
    };
    private final IrIntExprVisitor<Void, IntVar> intExprCompiler = new IrIntExprVisitor<Void, IntVar>() {
        /**
         * TODO: optimize
         *
         * 5 = x + y
         *
         * sum([x,y], newVar) newVar = 5
         *
         * Instead pass "5" in the Void param so sum([x,y], 5)
         */
        @Override
        public IntVar visit(IrIntLiteral ir, Void a) {
            return intVar.get(ir.getVar());
        }

        @Override
        public IntVar visit(IrCard ir, Void a) {
            return setCardVar.get(ir.getSet().accept(setExprCompiler, a));
        }

        @Override
        public IntVar visit(IrArithm ir, Void a) {
            int constants;
            IrIntExpr[] operands = ir.getOperands();
            Deque<IntVar> filter = new LinkedList<IntVar>();
            switch (ir.getOp()) {
                case Add: {
                    constants = 0;
                    for (IrIntExpr operand : operands) {
                        Integer constant = IrUtil.getConstant(operand);
                        if (constant != null) {
                            constants += constant.intValue();
                        } else {
                            filter.add(operand.accept(this, a));
                        }
                    }
                    IntVar[] addends = filter.toArray(new IntVar[filter.size()]);
                    switch (addends.length) {
                        case 0:
                            // This case should have already been optimized earlier.
                            return VariableFactory.fixed(constants, solver);
                        case 1:
                            return VariableFactory.offset(addends[0], constants);
                        case 2:
                            return VariableFactory.offset(_sum(addends[0], addends[1]), constants);
                        default:
                            IntVar sum = numIntVar("Sum", ir.getDomain());
                            solver.post(_sum(sum, addends));
                            return VariableFactory.offset(sum, constants);
                    }
                }
                case Sub: {
                    constants = 0;
                    for (int i = 1; i < operands.length; i++) {
                        Integer constant = IrUtil.getConstant(operands[i]);
                        if (constant != null) {
                            constants += constant.intValue();
                        } else {
                            filter.add(operands[i].accept(this, a));
                        }
                    }
                    Integer constant = IrUtil.getConstant(operands[0]);
                    if (constant != null) {
                        IntVar[] subtractends = filter.toArray(new IntVar[filter.size()]);
                        switch (subtractends.length) {
                            case 0:
                                return VariableFactory.fixed(constant - constants, solver);
                            case 1:
                                return VariableFactory.offset(VariableFactory.minus(subtractends[0]), constant - constants);
                            case 2:
                                return VariableFactory.offset(VariableFactory.minus(_sum(subtractends[0], subtractends[1])), constant - constants);
                            default:
                                IntVar diff = numIntVar("Diff", ir.getDomain());
                                solver.post(_difference(diff, Util.cons(VariableFactory.fixed(constant - constants, solver), subtractends)));
                                return VariableFactory.offset(diff, -constants);
                        }
                    }
                    filter.add(operands[0].accept(this, a));
                    IntVar[] subtractends = filter.toArray(new IntVar[filter.size()]);
                    switch (subtractends.length) {
                        case 1:
                            return VariableFactory.offset(subtractends[0], -constants);
                        case 2:
                            return VariableFactory.offset(_sum(subtractends[0],
                                    VariableFactory.minus(subtractends[1])), -constants);
                        default:
                            IntVar diff = numIntVar("Diff", ir.getDomain());
                            solver.post(_difference(diff, subtractends));
                            return VariableFactory.offset(diff, -constants);
                    }
                }
                case Mul: {
                    // TODO: assert operands.length == 2
                    constants = 1;
                    for (IrIntExpr operand : operands) {
                        Integer constant = IrUtil.getConstant(operand);
                        if (constant != null) {
                            constants *= constant.intValue();
                        } else {
                            filter.add(operand.accept(this, a));
                        }
                    }
                    if (filter.isEmpty()) {
                        // This case should have already been optimized earlier.
                        return VariableFactory.fixed(constants, solver);
                    }
                    if (constants < -1) {
                        filter.add(VariableFactory.fixed(constants, solver));
                    }
                    IntVar[] multiplicands = filter.toArray(new IntVar[filter.size()]);
                    switch (multiplicands.length) {
                        case 1:
                            return constants < -1 ? multiplicands[0]
                                    : VariableFactory.scale(multiplicands[0], constants);
                        default:
                            IntVar multiplicand = multiplicands[0];
                            for (int i = 1; i < multiplicands.length; i++) {
                                IntVar product = numIntVar("Mul", ir.getDomain());
                                solver.post(_times(multiplicand, multiplicands[i], product));
                                multiplicand = product;
                            }
                            return constants < -1 ? multiplicand : VariableFactory.scale(multiplicand, constants);
                    }
                }
                case Div: {
                    // TODO: assert operands.length == 2
                    IntVar divisor = operands[0].accept(this, a);
                    for (int i = 1; i < operands.length; i++) {
                        IntVar quotient = numIntVar("Div", ir.getDomain());
                        solver.post(_times(divisor, operands[i].accept(this, a), quotient));
                        divisor = quotient;
                    }
                    return divisor;
                }
            }
            throw new IrException();
        }

        @Override
        public IntVar visit(IrElement ir, Void a) {
            IrIntExpr index = ir.getIndex();
            IrIntExpr[] array = ir.getArray();

            IntVar $index = index.accept(intExprCompiler, a);
            IntVar[] $array = new IntVar[array.length];
            for (int i = 0; i < $array.length; i++) {
                $array[i] = array[i].accept(intExprCompiler, a);
            }
            IntVar element = numIntVar("Element", getLB($array), getUB($array));
            solver.post(_element($index, $array, element));
            return element;
        }
    };
    private final IrSetExprVisitor<Void, SetVar> setExprCompiler = new IrSetExprVisitor<Void, SetVar>() {
        @Override
        public SetVar visit(IrSetLiteral ir, Void a) {
            return setVar.get(ir.getVar());
        }

        @Override
        public SetVar visit(IrSingleton ir, Void a) {
            IrIntExpr value = ir.getValue();
            IntVar $value = value.accept(intExprCompiler, a);
            SetVar singleton = numSetVar("Singleton", ir.getEnv(), ir.getKer());
            solver.post(Constraints.singleton($value, singleton));
            return singleton;
        }

        @Override
        public SetVar visit(IrArrayToSet ir, Void a) {
            IrIntExpr[] array = ir.getArray();
            IntVar[] $array = new IntVar[array.length];
            for (int i = 0; i < $array.length; i++) {
                $array[i] = array[i].accept(intExprCompiler, a);
            }
            SetVar set = numSetVar("ArrayToSet", ir.getEnv(), ir.getKer());
            solver.post(Constraints.arrayToSet($array, set));
            return set;
        }

        @Override
        public SetVar visit(IrJoin ir, Void a) {
            IrSetExpr take = ir.getTake();
            IrSetExpr[] children = ir.getChildren();
            SetVar $take = take.accept(this, a);
            SetVar[] $children = new SetVar[children.length];
            for (int i = 0; i < $children.length; i++) {
                $children[i] = children[i].accept(this, a);
            }
            SetVar join = numSetVar("Join", ir.getEnv(), ir.getKer());
            solver.post(Constraints.join($take, $children, join));
            return join;
        }

        @Override
        public SetVar visit(IrJoinRef ir, Void a) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public SetVar visit(IrUnion ir, Void a) {
            IrSetExpr[] operands = ir.getOperands();
            SetVar[] $operands = new SetVar[operands.length];
            for (int i = 0; i < operands.length; i++) {
                $operands[i] = operands[i].accept(setExprCompiler, a);
            }
            SetVar union = numSetVar("Union", ir.getEnv(), ir.getKer());
            solver.post(_union($operands, union));
            return union;
        }
    };

    private ConjunctiveNormalForm _clauses(LogOp tree) {
        return IntConstraintFactory.clauses(tree, solver);
    }

    private static Constraint _implies(BoolVar b, Constraint c) {
        return LogicalConstraintFactory.ifThen(b, c);
    }

    private static Constraint _ifThenElse(BoolVar b, Constraint c, Constraint d) {
        return LogicalConstraintFactory.ifThenElse(b, c, d);
    }

    private static IntVar _sum(IntVar var1, IntVar var2) {
        return Sum.var(var1, var2);
    }

    private static Constraint _difference(IntVar difference, IntVar... vars) {
        int[] coeffiecients = new int[vars.length];
        coeffiecients[0] = 1;
        for (int i = 1; i < coeffiecients.length; i++) {
            coeffiecients[i] = -1;
        }
        return IntConstraintFactory.scalar(vars, coeffiecients, difference);
    }

    private static Constraint _sum(IntVar sum, IntVar... vars) {
        return IntConstraintFactory.sum(vars, sum);
    }

    private static Constraint _sum(IntVar sum, BoolVar... vars) {
        return IntConstraintFactory.sum(vars, sum);
    }

    private static Constraint _times(IntVar var1, IntVar var2, IntVar product) {
        return IntConstraintFactory.times(var1, var2, product);
    }

    private static Constraint _arithm(IntVar var1, String op1, IntVar var2, String op2, int cste) {
        return IntConstraintFactory.arithm(var1, op1, var2, op2, cste);
    }

    private static Constraint _and(BoolVar... vars) {
        switch (vars.length) {
            case 1:
                return _arithm(vars[0], "=", 1);
            case 2:
                return _arithm(vars[0], "+", vars[1], "=", 2);
            default:
                // Better than the one provided by the library.
                return _sum(VariableFactory.fixed(vars.length, vars[0].getSolver()), vars);
        }
    }

    private static Constraint _or(BoolVar... vars) {
        switch (vars.length) {
            case 1:
                return _arithm(vars[0], "=", 1);
            case 2:
                return _arithm(vars[0], "+", vars[1], ">=", 1);
            default:
                return LogicalConstraintFactory.or(vars);
        }
    }

    private static Constraint _implies(BoolVar antecedent, BoolVar consequent) {
        return _arithm(antecedent, "<=", consequent);
    }

    private static Constraint _not_implies(BoolVar antecedent, BoolVar consequent) {
        return _arithm(antecedent, ">", consequent);
    }

    private static Constraint _arithm(IntVar var1, String op, IntVar var2) {
        if (var2.instantiated()) {
            return IntConstraintFactory.arithm(var1, op, var2.getValue());
        }
        return IntConstraintFactory.arithm(var1, op, var2);
    }

    private static Constraint _arithm(IntVar var1, String op, int c) {
        return IntConstraintFactory.arithm(var1, op, c);
    }

    private static Constraint _element(IntVar index, IntVar[] array, IntVar value) {
        return IntConstraintFactory.element(value, array, index, 0);
    }

    private static Constraint _equal(SetVar var1, SetVar var2) {
        return Constraints.equal(var1, var2);
    }

    private static Constraint _all_different(SetVar... vars) {
        return SetConstraintsFactory.all_different(vars);
    }

    private static Constraint _all_different(IntVar... vars) {
        return IntConstraintFactory.alldifferent(vars, "AC");
    }

    private static Constraint _between(IntVar var, int low, int high) {
        return IntConstraintFactory.member(var, low, high);
    }

    private static Constraint _not_between(IntVar var, int low, int high) {
        return IntConstraintFactory.not_member(var, low, high);
    }

    private static Constraint _member(IntVar element, SetVar set) {
        return SetConstraintsFactory.member(element, set);
    }

    private static Constraint _not_member(IntVar element, SetVar set) {
        return Constraints.notMember(element, set);
    }

    private static Constraint _lex_chain_less_eq(IntVar[]... vars) {
        if (vars.length == 2) {
            return IntConstraintFactory.lex_less_eq(vars[0], vars[1]);
        }
        return IntConstraintFactory.lex_chain_less_eq(vars);
    }

    private static Constraint _union(SetVar[] operands, SetVar union) {
        return SetConstraintsFactory.union(operands, union);
    }

    private int getLB(IntVar... vars) {
        if (vars.length < 0) {
            throw new IllegalArgumentException();
        }
        int lb = vars[0].getLB();
        for (int i = 1; i < vars.length; i++) {
            lb = Math.min(lb, vars[i].getLB());
        }
        return lb;
    }

    private int getUB(IntVar... vars) {
        if (vars.length < 0) {
            throw new IllegalArgumentException();
        }
        int ub = vars[0].getUB();
        for (int i = 1; i < vars.length; i++) {
            ub = Math.max(ub, vars[i].getUB());
        }
        return ub;
    }

    private static enum Preference {

        Constraint,
        BoolVar;
    }

    public static void main(String[] args) {
    }
}
