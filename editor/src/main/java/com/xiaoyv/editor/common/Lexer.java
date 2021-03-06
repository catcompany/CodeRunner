/*
 * Copyright (c) 2013 Tah Wei Hoon.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License Version 2.0,
 * with full text available at http://www.apache.org/licenses/LICENSE-2.0.html
 *
 * This software is provided "as is". Use at your own risk.
 */
package com.xiaoyv.editor.common;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;


/**
 * Does lexical analysis of a text for C-like languages.
 * The programming language syntax used is set as a static class variable.
 */
public class Lexer {
    public static final int LEXER_NORMAL = 0;
    public static final int LEXER_KEYWORD = 1;
    public static final int LEXER_COMMENT = 2;
    public static final int LEXER_STRING = 3;
    public static final int LEXER_NUMBER = 4;
    public static final int LEXER_IDENTIFIER = 5;
    public static final int LEXER_OPERATOR = 6;
    public static final int LEXER_SYMBOL = 7;
    public static final int LEXER_OTHER = 8;

    private static Language globalLanguage = LanguageNonPro.getInstance();

    private DocumentProvider documentProvider;
    private LexThread lexThread = null;
    private LexCallback lexCallback;


    synchronized public static void setLanguage(Language lang) {
        globalLanguage = lang;
    }

    synchronized public static Language getLanguage() {
        return globalLanguage;
    }


    public Lexer(LexCallback callback) {
        lexCallback = callback;
    }

    public void tokenize(DocumentProvider documentProvider) {
        if (!Lexer.getLanguage().isProLang()) {
            return;
        }

        setDocument(new DocumentProvider(documentProvider));
        if (lexThread == null) {
            lexThread = new LexThread(this);
            lexThread.start();
        } else {
            lexThread.restart();
        }
    }

    private void tokenizeDone(List<Pair> pairList) {
        if (lexCallback != null) {
            lexCallback.lexDone(pairList);
        }
        lexThread = null;
    }

    public void cancelTokenize() {
        if (lexThread != null) {
            lexThread.abort();
            lexThread = null;
        }
    }

    public synchronized void setDocument(DocumentProvider document) {
        documentProvider = document;
    }

    public synchronized DocumentProvider getDocument() {
        return documentProvider;
    }


    private class LexThread extends Thread {
        private boolean rescan = false;
        private final Lexer lexManager;
        // ?????????????????????????????????????????????
        private final Flag abortFlag;
        // Pair.first ???tokenize??????????????????Pair.second ???tokenize?????????
        private List<Pair> pairList;

        public LexThread(Lexer lexer) {
            lexManager = lexer;
            abortFlag = new Flag();
        }

        @Override
        public void run() {
            do {
                rescan = false;
                abortFlag.clear();
                tokenize();
            }
            while (rescan);

            if (!abortFlag.isSet()) {
                // ??????????????????
                lexManager.tokenizeDone(pairList);
            }
        }

        public void restart() {
            rescan = true;
            abortFlag.set();
        }

        public void abort() {
            abortFlag.set();
        }

        public void tokenize() {
            DocumentProvider document = getDocument();
            Language language = Lexer.getLanguage();
            List<Pair> tokenPairs = new ArrayList<>();

            // ?????????????????????????????????
            if (!language.isProLang()) {
                tokenPairs.add(new Pair(0, LEXER_NORMAL));
                pairList = tokenPairs;
                return;
            }
            // ??????????????????????????????
            StringReader stringReader = new StringReader(document.toString());
            // ???????????????????????????
            JavaLexer javaLexer = new JavaLexer(stringReader);
            // ?????????????????????
            JavaType javaType = null;
            // ??????
            int idx = 0;
            // ???????????????
            String identifier = null;
            // ?????????????????????
            String identifierType = null;
            // ??????????????????????????????
            language.clearDefendMapKey();

            while (javaType != JavaType.EOF) {
                try {
                    javaType = javaLexer.yylex();
                    switch (javaType) {
                        case KEYWORD:                // ?????????
                        case NULL_LITERAL:           // null ??????
                        case BOOLEAN_LITERAL:        // boolean ??????
                            tokenPairs.add(new Pair(idx, LEXER_KEYWORD));
                            break;
                        // ??????
                        case COMMENT:
                            tokenPairs.add(new Pair(idx, LEXER_COMMENT));
                            break;
                        case STRING:                   // ?????????
                        case CHARACTER_LITERAL:        // ??????
                            tokenPairs.add(new Pair(idx, LEXER_STRING));
                            break;
                        // ??????
                        case INTEGER_LITERAL:
                        case FLOATING_POINT_LITERAL:
                            tokenPairs.add(new Pair(idx, LEXER_NUMBER));
                            break;
                        case EOF:                    // EOF
                            tokenPairs.add(new Pair(idx, LEXER_IDENTIFIER));
                            break;
                        // ?????????
                        case IDENTIFIER:
                            identifier = javaLexer.yytext();
                            tokenPairs.add(new Pair(idx, LEXER_IDENTIFIER));
                            break;
                        // ??????
                        case LPAREN:       // (
                        case LBRACK:       // [
                        case RBRACK:       // ]
                        case LBRACE:       // {
                        case RBRACE:       // }
                        case LT:           // <
                        case GT:           // >
                        case COMMA:        // ,
                        case COMP:         // ~
                        case COLON:        // :
                        case DOT:          // .
                        case SEMICOLON:    // ;
                        case EQ:           // =
                        case RPAREN:       // )
                        case WHITESPACE:             // ??????
                            // ?????????
                            tokenPairs.add(new Pair(idx, LEXER_SYMBOL));
                            if (identifier != null) {
                                language.addDefendMapKey(identifier);
                                identifier = null;
                            }
                            break;
                        // ?????????
                        case PLUS:         // +
                        case MINUS:        // -
                        case PLUSPLUS:     // ++
                        case PLUSEQ:       // +=
                        case MINUSMINUS:   // --
                        case MINUSEQ:      // -=
                        case DIV:          // /
                        case DIVEQ:        // /=
                        case MULT:         // *
                        case MULTEQ:       // *=
                        case MOD:          // %
                        case MODEQ:        // %=
                        case OR:           // |
                        case OROR:         // ||
                        case OREQ:         // |=
                        case XOR:          // ^
                        case XOREQ:        // ^=
                        case LTEQ:         // <=
                        case GTEQ:         // >=
                        case AND:          // &
                        case ANDAND:       // &&
                        case ANDEQ:        // &=
                        case NOT:          // !
                        case NOTEQ:        // !=
                        case QUESTION:     // ?
                        case LSHIFT:       // <<
                        case LSHIFTEQ:     // <<=
                        case RSHIFT:       // >>
                        case URSHIFT:      // >>>
                        case RSHIFTEQ:     // >>=
                        case URSHIFTEQ:    // >>>=
                            tokenPairs.add(new Pair(idx, LEXER_OPERATOR));
                            break;
                        // ??????????????????
                        default:
                            tokenPairs.add(new Pair(idx, LEXER_OTHER));
                            break;
                    }
                    idx += javaLexer.yytext().length();
                } catch (Exception e) {
                    e.printStackTrace();
                    // ?????????????????????????????????
                    idx++;
                }
            }

            if (tokenPairs.isEmpty()) {
                // ????????????????????????????????????
                tokenPairs.add(new Pair(0, LEXER_NORMAL));
            }
            pairList = tokenPairs;
        }
    }


    public interface LexCallback {
        void lexDone(List<Pair> results);
    }
}
