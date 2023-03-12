package org.plantuml.compiler;

import org.clafer.ast.AstModel;
import org.plantuml.ast.PlantumlProgram;

/**
 * Clafer AST to PlantUML
 *
 * Note that this compilation doesn't require instances, so we don't need to run the solver
 * to compile.
 */
public class AstPlantumlCompiler {
    public PlantumlProgram compile(AstModel model) {
        return new PlantumlProgram();
    }
}
