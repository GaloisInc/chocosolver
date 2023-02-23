package org.sysml;

/**
 * Sysml Block Visibility
 * <block-visibility> ::=<namespace-visibility> | ‘#’ | ‘~’
 */
public class SysmlBlockVisibility {
    public final SysmlVisibilityOption option;

    public SysmlBlockVisibility(SysmlVisibilityOption visOpt) {
        this.option = visOpt;
    }

}
