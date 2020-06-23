#!/usr/bin/env ruby

# Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'erb'

file = 'tool/id.def'
ids = eval(File.read(file), binding, file)
types = ids.keys.grep(/^[A-Z]/)

character_ids = ('!'..'~').select {|c| c !~ /^[[:alnum:]]$/ && c != '_' }.to_a
file = __FILE__

names = {
  "!" => "BANG",
  "\"" => "DOUBLE_QUOTE",
  "#" => "POUND",
  "$" => "DOLLAR",
  "%" => "MODULO",
  "&" => "AMPERSAND",
  "'" => "SINGLE_QUOTE",
  "(" => "LPAREN",
  ")" => "RPAREN",
  "*" => "MULTIPLY",
  "+" => "PLUS",
  "," => "COMMA",
  "-" => "MINUS",
  "." => "PERIOD",
  "/" => "DIVIDE",
  ":" => "COLON",
  ";" => "SEMICOLON",
  "<" => "LESS_THAN",
  "=" => "EQUAL",
  ">" => "GREATER_THAN",
  "?" => "QUESTION_MARK",
  "@" => "AT_SYMBOL",
  "[" => "LEFT_BRACKET",
  "\\" => "BACK_SLASH",
  "]" => "RIGHT_BRACKET",
  "^" => "CIRCUMFLEX",
  "`" => "BACK_TICK",
  "{" => "LEFT_BRACE",
  "|" => "PIPE",
  "}" => "RIGHT_BRACE",
  "~" => "TILDE",
}

ids_map = {}
character_ids.each do |id|
  ids_map[id.ord] = { :id => id, :name => names[id] }
end

offset = 128

index = offset
# ids[:token_op].each do |_id, _op, token|
#  next unless token
#  index += 1
# end

max_index = ids_map.keys.max


File.write('src/main/java/org/truffleruby/core/symbol/CoreSymbols.java', ERB.new(<<'JAVA').result)
/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.symbol;

import java.util.ArrayList;
import java.util.List;

import org.jcodings.specific.USASCIIEncoding;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.rope.RopeOperations;

// GENERATED BY <%= file %>
// This file is automatically generated from tool/id.def with 'jt build core-symbols'

// @formatter:off
public class CoreSymbols {

    public static final List<RubySymbol> CORE_SYMBOLS = new ArrayList<>();
    public static final RubySymbol[] STATIC_SYMBOLS = new RubySymbol[216];

    public static final RubySymbol CLASS = createRubySymbol("class");
    public static final RubySymbol DIVMOD = createRubySymbol("divmod");
    public static final RubySymbol IMMEDIATE = createRubySymbol("immediate");
    public static final RubySymbol LINE = createRubySymbol("line");
    public static final RubySymbol NEVER = createRubySymbol("never");
    public static final RubySymbol ON_BLOCKING = createRubySymbol("on_blocking");

<% ids_map.each do |key, value|  %>
    public static final RubySymbol <%= value[:name] %> = CoreSymbols.createRubySymbol(<%= value[:id].inspect %>, <%= key %>);<% end %>

<% ids[:token_op].uniq {|_, op| op}.each do |id, op, token| %><% if token %>
    public static final RubySymbol <%=token%> = createRubySymbol("<%=op%>", <%=index%>);<% index += 1 %><% end %><% end %>
<% ids[:preserved].each do |token| %><% if ids[:predefined][token] %>
    public static final RubySymbol <%=token.start_with?('_') ? token[1..].upcase : token.upcase%> = createRubySymbol("<%=token == 'NULL' ?  '' : ids[:predefined][token]%>", <%=index%>);<% index += 1 %><% else %>
    // Skipped preserved token: `<%=token%>`<% index += 1 %><% end %><% end %>
    public static final int LAST_OP_ID = <%=index-1%>;
<% types.each do |type| %><% tokens = ids[type] %><% tokens.each do |token| %>
    public static final RubySymbol <%=token.upcase%> = createRubySymbol("<%=ids[:predefined][token]%>", <%=index%>);<% index += 1 %><% end %><% end %>

    public static final int STATIC_SYMBOLS_SIZE = <%=index%>;
    static {
        assert STATIC_SYMBOLS_SIZE == STATIC_SYMBOLS.length;
    }

    public static RubySymbol createRubySymbol(String string, int id) {
        Rope rope = RopeConstants.lookupUSASCII(string);
        if (rope == null) {
            rope = RopeOperations.encodeAscii(string, USASCIIEncoding.INSTANCE);
        }

        final RubySymbol symbol = new RubySymbol(string, rope, id);
        CORE_SYMBOLS.add(symbol);
        if (id != RubySymbol.UNASSIGNED) {
            STATIC_SYMBOLS[id] = symbol;
        }
        return symbol;
    }

    public static RubySymbol createRubySymbol(String string) {
        return createRubySymbol(string, RubySymbol.UNASSIGNED);
    }
}
// @formatter:on
JAVA

