package org.plantuml.ast;

import java.io.IOException;

/**
 * AST Visitor
 *
 * We make AST visitors capable of throwing IOExecptions as it's convenient for pretty printers
 * However, we could likely get rid of this throw some type of interface conversion.
 */
public interface PlantumlExprVisitor<A, B> {
    B visit(PlantumlProgram plantumlProgram, A a) throws IOException;
}
