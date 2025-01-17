/*
 * Copyright (c) 2015, 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.cast;

import com.oracle.truffle.api.library.CachedLibrary;
import org.truffleruby.core.string.StringCachingGuards;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;
import org.truffleruby.language.RubyBaseNodeWithExecute;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.library.RubyStringLibrary;
import org.truffleruby.utils.Utils;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;

/** Converts a method name to a Java String. The exception message below assumes this conversion is done for a method
 * name. */
@ImportStatic({ StringCachingGuards.class, StringOperations.class })
@GenerateUncached
@NodeChild(value = "value", type = RubyBaseNodeWithExecute.class)
public abstract class NameToJavaStringNode extends RubyBaseNodeWithExecute {

    public static NameToJavaStringNode create() {
        return NameToJavaStringNodeGen.create(null);
    }

    public static NameToJavaStringNode create(RubyBaseNodeWithExecute name) {
        return NameToJavaStringNodeGen.create(name);
    }

    public static NameToJavaStringNode getUncached() {
        return NameToJavaStringNodeGen.getUncached();
    }

    public abstract String execute(Object name);

    @Specialization(guards = "strings.isRubyString(value)")
    protected String stringNameToJavaString(Object value,
            @CachedLibrary(limit = "LIBSTRING_CACHE") RubyStringLibrary strings,
            @Cached ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.executeToJavaString(value);
    }

    @Specialization
    protected String symbolNameToJavaString(RubySymbol value,
            @Cached ToJavaStringNode toJavaStringNode) {
        return toJavaStringNode.executeToJavaString(value);
    }

    @Specialization
    protected String nameToJavaString(String value) {
        return value;
    }

    @Specialization(guards = { "!isString(object)", "!isRubySymbol(object)", "isNotRubyString(object)" })
    protected String nameToJavaString(Object object,
            @Cached BranchProfile errorProfile,
            @Cached DispatchNode toStr,
            @CachedLibrary(limit = "LIBSTRING_CACHE") RubyStringLibrary libString) {
        final Object coerced;
        try {
            coerced = toStr.call(object, "to_str");
        } catch (RaiseException e) {
            errorProfile.enter();
            if (e.getException().getLogicalClass() == coreLibrary().noMethodErrorClass) {
                throw new RaiseException(getContext(), coreExceptions().typeError(
                        Utils.concat(object, " is not a symbol nor a string"),
                        this));
            } else {
                throw e;
            }
        }

        if (libString.isRubyString(coerced)) {
            return libString.getJavaString(coerced);
        } else {
            errorProfile.enter();
            throw new RaiseException(getContext(), coreExceptions().typeErrorBadCoercion(
                    object,
                    "String",
                    "to_str",
                    coerced,
                    this));
        }
    }

}
