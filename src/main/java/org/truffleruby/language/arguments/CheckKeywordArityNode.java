/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.arguments;

import com.oracle.truffle.api.CompilerDirectives;
import org.truffleruby.RubyLanguage;
import org.truffleruby.collections.BiConsumerNode;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.hash.HashNodes.EachKeyValueNode;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.language.RubyContextNode;
import org.truffleruby.language.RubyContextSourceNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.methods.Arity;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public class CheckKeywordArityNode extends RubyContextSourceNode {

    private final Arity arity;
    @Child private RubyNode body;

    @Child private ReadUserKeywordsHashNode readUserKeywordsHashNode;
    @Child private CheckKeywordArgumentsNode checkKeywordArgumentsNode;
    @Child private EachKeyValueNode eachKeyNode;

    private final BranchProfile receivedKeywordsProfile = BranchProfile.create();
    private final BranchProfile basicArityCheckFailedProfile = BranchProfile.create();

    public CheckKeywordArityNode(RubyLanguage language, Arity arity, RubyNode body) {
        this.arity = arity;
        this.body = body;
        this.readUserKeywordsHashNode = new ReadUserKeywordsHashNode(arity.getRequired());
    }

    @Override
    public void doExecuteVoid(VirtualFrame frame) {
        checkArity(frame);
        body.doExecuteVoid(frame);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        checkArity(frame);
        return body.execute(frame);
    }

    private void checkArity(VirtualFrame frame) {
        final RubyHash keywordArguments = readUserKeywordsHashNode.execute(frame);

        int given = RubyArguments.getArgumentsCount(frame);

        if (keywordArguments != null) {
            receivedKeywordsProfile.enter();
            given -= 1;
        }

        if (!arity.basicCheck(given)) {
            basicArityCheckFailedProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().argumentError(given, arity.getRequired(), this));
        }

        if (!arity.hasKeywordsRest() && keywordArguments != null) {
            checkKeywordArguments(frame, keywordArguments);
        }
    }

    void checkKeywordArguments(VirtualFrame frame, RubyHash keywordArguments) {
        if (eachKeyNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            eachKeyNode = insert(EachKeyValueNode.create());
        }
        if (checkKeywordArgumentsNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            checkKeywordArgumentsNode = insert(new CheckKeywordArgumentsNode(getLanguage(), arity));
        }
        eachKeyNode.executeEachKeyValue(frame, keywordArguments, checkKeywordArgumentsNode, null);
    }

    private static class CheckKeywordArgumentsNode extends RubyContextNode implements BiConsumerNode {

        private final boolean doesNotAcceptExtraArguments;
        private final int required;
        @CompilationFinal(dimensions = 1) private final RubySymbol[] allowedKeywords;

        private final ConditionProfile isSymbolProfile = ConditionProfile.create();
        private final BranchProfile tooManyKeywordsProfile = BranchProfile.create();
        private final BranchProfile unknownKeywordProfile = BranchProfile.create();

        public CheckKeywordArgumentsNode(RubyLanguage language, Arity arity) {
            assert !arity.hasKeywordsRest();
            doesNotAcceptExtraArguments = !arity.hasRest() && arity.getOptional() == 0;
            required = arity.getRequired();
            allowedKeywords = keywordsAsSymbols(language, arity);
        }

        @Override
        public void accept(VirtualFrame frame, Object key, Object value, Object state) {
            if (isSymbolProfile.profile(key instanceof RubySymbol)) {
                if (!keywordAllowed(key)) {
                    unknownKeywordProfile.enter();
                    throw new RaiseException(
                            getContext(),
                            coreExceptions().argumentErrorUnknownKeyword((RubySymbol) key, this));
                }
            } else {
                // the Hash would be split and a reject Hash be created to hold non-Symbols when there is no **kwrest parameter,
                // so we need to check if an extra argument is allowed
                final int given = RubyArguments.getArgumentsCount(frame); // -1 for keyword hash, +1 for reject Hash with non-Symbol keys
                if (doesNotAcceptExtraArguments && given > required) {
                    tooManyKeywordsProfile.enter();
                    throw new RaiseException(getContext(), coreExceptions().argumentError(given, required, this));
                }
            }

        }

        @ExplodeLoop
        private boolean keywordAllowed(Object keyword) {
            for (int i = 0; i < allowedKeywords.length; i++) {
                if (allowedKeywords[i] == keyword) {
                    return true;
                }
            }

            return false;
        }

    }

    static RubySymbol[] keywordsAsSymbols(RubyLanguage language, Arity arity) {
        final String[] names = arity.getKeywordArguments();
        final RubySymbol[] symbols = new RubySymbol[names.length];
        for (int i = 0; i < names.length; i++) {
            symbols[i] = language.getSymbol(names[i]);
        }
        return symbols;
    }

}
