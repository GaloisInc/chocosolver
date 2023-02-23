package org.sysml;

import org.clafer.ast.AstThis;

import java.io.IOException;

public interface SysmlExprVisitor <A, B>{
    B visit(SysmlPackage ast, A a) throws IOException;

    B visit(SysmlProperty ast, A a);
}
