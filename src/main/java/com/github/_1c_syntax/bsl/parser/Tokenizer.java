/*
 * This file is a part of BSL Parser Core.
 *
 * Copyright (c) 2018-2024
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com>, Valery Maximov <maximovvalery@gmail.com>
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Parser Core is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Parser Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Parser Core.
 */
package com.github._1c_syntax.bsl.parser;

import com.github._1c_syntax.utils.Lazy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;
import static org.antlr.v4.runtime.Token.EOF;

/**
 * Базовая реализация токенайзера
 *
 * @param <T> Класс контекста
 * @param <P> Класс реализации парсера
 */
public abstract class Tokenizer<T extends BSLParserRuleContext, P extends Parser> {

  private String content;
  private final Lexer lexer;
  private final Lazy<CommonTokenStream> tokenStream = new Lazy<>(this::computeTokenStream);
  private final Lazy<List<Token>> tokens = new Lazy<>(this::computeTokens);
  private final Lazy<T> ast = new Lazy<>(this::computeAST);
  private final Class<P> parserClass;
  protected P parser;

  private static final Map<Class<?>, Constructor<?>> CONSTRUCTORS = new ConcurrentHashMap<>();

  protected Tokenizer(String content, Lexer lexer, Class<P> parserClass) {
    requireNonNull(content);
    requireNonNull(lexer);
    this.content = content;
    this.lexer = lexer;
    this.parserClass = parserClass;
  }

  /**
   * Возвращает список токенов, полученных при парсинге
   *
   * @return Список токенов
   */
  public List<Token> getTokens() {
    return tokens.getOrCompute();
  }

  /**
   * Возвращает абстрактное синтаксическое дерево, полученное на основании парсинга
   *
   * @return AST
   */
  public T getAst() {
    return ast.getOrCompute();
  }

  private List<Token> computeTokens() {
    var tokensTemp = getTokenStream().getTokens();
    var lastToken = tokensTemp.get(tokensTemp.size() - 1);
    if (lastToken.getType() == EOF && lastToken instanceof CommonToken commonToken) {
      commonToken.setChannel(Lexer.HIDDEN);
    }

    return tokensTemp;
  }

  private T computeAST() {
    parser = createParser(getTokenStream());
    parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
    try {
      parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    } catch (Exception ex) {
      parser.reset(); // rewind input stream
      parser.getInterpreter().setPredictionMode(PredictionMode.LL);
    }
    return rootAST();
  }

  protected abstract T rootAST();

  private CommonTokenStream computeTokenStream() {
    lexer.setInputStream(CharStreams.fromString(content));
    content = null;
    lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
    var tempTokenStream = new CommonTokenStream(lexer);
    tempTokenStream.fill();

    return tempTokenStream;
  }

  protected CommonTokenStream getTokenStream() {
    final CommonTokenStream tokenStreamUnboxed = tokenStream.getOrCompute();
    tokenStreamUnboxed.seek(0);
    return tokenStreamUnboxed;
  }

  @SuppressWarnings("unchecked")
  private P createParser(CommonTokenStream tokenStream) {
    try {
      return (P) CONSTRUCTORS.computeIfAbsent(parserClass, (Class<?> k) -> {
          try {
            return parserClass.getDeclaredConstructor(TokenStream.class);
          } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        })
        .newInstance(tokenStream);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}
