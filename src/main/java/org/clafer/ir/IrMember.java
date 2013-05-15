package org.clafer.ir;

import org.clafer.Check;

/**
 *
 * @author jimmy
 */
public class IrMember implements IrBoolExpr {

    private final IrIntExpr element;
    private final IrSetExpr set;

    IrMember(IrIntExpr element, IrSetExpr set) {
        this.element = Check.notNull(element);
        this.set = Check.notNull(set);
    }

    public IrIntExpr getElement() {
        return element;
    }

    public IrSetExpr getSet() {
        return set;
    }

    @Override
    public IrBoolExpr opposite() {
        return new IrNotMember(element, set);
    }

    @Override
    public <A, B> B accept(IrBoolExprVisitor<A, B> visitor, A a) {
        return visitor.visit(this, a);
    }
}
