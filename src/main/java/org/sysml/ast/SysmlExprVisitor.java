package org.sysml.ast;

import java.io.IOException;

public interface SysmlExprVisitor <A, B>{
    B visit(SysmlPackage ast, A a) throws IOException;

    B visit(SysmlProperty ast, A a);

    B visit(SysmlAnnotation ast, A a) throws IOException;
}
