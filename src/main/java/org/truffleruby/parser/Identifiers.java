/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: EPL 2.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Eclipse Public
 * License Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/epl-v20.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2001-2002 Jan Arne Petersen <jpetersen@uni-bonn.de>
 * Copyright (C) 2007 William N Dortch <bill.dortch@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the EPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the EPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.truffleruby.parser;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

// No charAt() in this class or hardcoded char offsets, we want to treat surrogate characters properly
public final class Identifiers {

    @TruffleBoundary
    public static boolean isValidConstantName(String id) {
        int c;
        if (id.length() > 0 && (c = id.codePointAt(0)) <= 'Z' && c >= 'A') {
            return isNameString(id, Character.charCount(c));
        }
        return false;
    }

    @TruffleBoundary
    public static boolean isValidLocalVariableName(String id) {
        if (id.isEmpty()) {
            return false;
        }
        int first = id.codePointAt(0);
        return Character.isLetter(first) && Character.isLowerCase(first) &&
                isNameString(id, Character.charCount(first));
    }

    @TruffleBoundary
    public static boolean isValidClassVariableName(String id) {
        return id.startsWith("@@") && isValidIdentifier(id, 2); // OK due to startsWith
    }

    @TruffleBoundary
    public static boolean isValidGlobalVariableName(String id) {
        return id.startsWith("$") && isValidIdentifier(id, 1); // OK due to startsWith
    }

    @TruffleBoundary
    public static boolean isValidInstanceVariableName(String id) {
        return id.startsWith("@") && isValidIdentifier(id, 1);  // OK due to startsWith
    }

    @TruffleBoundary
    private static boolean isValidIdentifier(String id, int start) {
        if (id.length() <= start) {
            return false;
        }
        int codePoint = id.codePointAt(start);
        return isInitialCharacter(codePoint) && isNameString(id, start + Character.charCount(codePoint));
    }

    // MRI: similar to is_identchar but does not allow numeric
    @TruffleBoundary
    private static boolean isInitialCharacter(int c) {
        return Character.isAlphabetic(c) || c == '_' || c >= 128;
    }

    @TruffleBoundary
    private static boolean isNameString(String id, int start) {
        final int length = id.length();
        int c;
        for (int i = start; i < length; i += Character.charCount(c)) {
            c = id.codePointAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                return false;
            }
        }
        return true;
    }

    public static IdentifierType stringToType(String id) {
        if (id.isEmpty()) {
            return IdentifierType.JUNK;
        } else {
            final int first = id.codePointAt(0);
            final int nextCodePoint = Character.charCount(first);
            switch (first) {
                case '\0':
                    return IdentifierType.JUNK;
                case '$':
                    return isValidIdentifier(id, nextCodePoint) ? IdentifierType.GLOBAL : IdentifierType.JUNK;
                case '@':
                    if (isValidClassVariableName(id)) {
                        return IdentifierType.CLASS;
                    } else if (id.length() > nextCodePoint && isInitialCharacter(id.codePointAt(nextCodePoint))) {
                        return IdentifierType.INSTANCE;
                    } else {
                        return IdentifierType.JUNK;
                    }
                default:
                    if (Character.isUpperCase(first) ||
                            (!Character.isLowerCase(first) && Character.isTitleCase(first))) {
                        return IdentifierType.CONST;
                    } else if (Character.isLetter(first) && Character.isLowerCase(first) &&
                            isNameString(id, nextCodePoint)) {
                        return IdentifierType.LOCAL;
                    } else {
                        return IdentifierType.JUNK;
                    }
            }
        }
    }

}
