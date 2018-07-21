/*
 * Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.constants;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import org.truffleruby.Layouts;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.RubyConstant;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;

/** Read a constant using a dynamic lexical scope: class << expr; CONST; end */
public class ReadConstantWithDynamicScopeNode extends RubyNode {

    private final String name;

    @Child private LookupConstantWithDynamicScopeNode lookupConstantNode;
    @Child private GetConstantNode getConstantNode = GetConstantNode.create();

    public ReadConstantWithDynamicScopeNode(String name) {
        this.name = name;
        this.lookupConstantNode = LookupConstantWithDynamicScopeNodeGen.create(name, null);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        final LexicalScope lexicalScope = RubyArguments.getMethod(frame).getLexicalScope();
        final RubyConstant constant = lookupConstantNode.executeLookupConstant(lexicalScope);
        final DynamicObject module = lexicalScope.getLiveModule();

        return getConstantNode.executeGetConstant(lexicalScope, module, name, constant, lookupConstantNode);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        final RubyConstant constant;
        final LexicalScope lexicalScope = RubyArguments.getMethod(frame).getLexicalScope();
        try {
            constant = lookupConstantNode.executeLookupConstant(lexicalScope);
        } catch (RaiseException e) {
            if (Layouts.BASIC_OBJECT.getLogicalClass(e.getException()) == coreLibrary().getNameErrorClass()) {
                // private constant
                return nil();
            }
            throw e;
        }

        if (constant == null) {
            return nil();
        } else {
            return coreStrings().CONSTANT.createInstance();
        }
    }

}
