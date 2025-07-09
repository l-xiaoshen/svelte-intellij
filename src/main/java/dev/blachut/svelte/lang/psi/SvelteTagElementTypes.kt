package dev.blachut.svelte.lang.psi

import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.parsing.FunctionParser
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.psi.tree.TokenSet
import dev.blachut.svelte.lang.SvelteBundle
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.parsing.html.SvelteTagParsing
import dev.blachut.svelte.lang.parsing.js.markupContextKey

object SvelteTagElementTypes {
  private object IF : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.SHARP
      SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
      builder.advanceLexer() // SvelteTokenTypes.IF_KEYWORD

      parser.expressionParser.parseExpression()
    }
  }

  val IF_START = createJS("IF_START", IF)
  val IF_START_TS = createTS("IF_START", IF)

  private object ELSE : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.COLON
      SvelteTagParsing.parseNotAllowedWhitespace(builder, ":")
      builder.advanceLexer() // SvelteTokenTypes.ELSE_KEYWORD

      if (builder.tokenType === SvelteTokenTypes.IF_KEYWORD) {
        builder.advanceLexer()
        parser.expressionParser.parseExpression()
      }
    }
  }

  val ELSE_CLAUSE = createJS("ELSE_CLAUSE", ELSE)
  val ELSE_CLAUSE_TS = createTS("ELSE_CLAUSE", ELSE)

  private object EACH : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.SHARP
      SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
      builder.remapCurrentToken(SvelteTokenTypes.EACH_KEYWORD) // todo might be okay to remove all those remapCurrentToken
      builder.advanceLexer() // JSTokenTypes.IDENTIFIER -- fake EACH_KEYWORD

      parser.expressionParser.parseExpression()

      if (builder.tokenType === SvelteTokenTypes.AS_KEYWORD) {
        builder.advanceLexer()
      } else {
        builder.error(SvelteBundle.message("svelte.parsing.error.as.expected"))
      }

      parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, false, false)

      if (builder.tokenType === JSTokenTypes.COMMA) {
        builder.advanceLexer()
        // TODO disallow destructuring
        parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, false, false)
      }

      if (builder.tokenType === JSTokenTypes.LPAR) {
        val keyExpressionMarker = builder.mark()
        builder.advanceLexer()
        parser.expressionParser.parseExpression()

        if (builder.tokenType === JSTokenTypes.RPAR) {
          builder.advanceLexer()
        } else {
          builder.error(SvelteBundle.message("svelte.parsing.error.expected.closing.brace"))
        }
        keyExpressionMarker.done(TAG_DEPENDENT_EXPRESSION)
      }
    }
  }

  val EACH_START = createJS("EACH_START", EACH)
  val EACH_START_TS = createTS("EACH_START", EACH)

  private object AWAIT : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.SHARP
      SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
      builder.advanceLexer() // SvelteTokenTypes.AWAIT_KEYWORD

//      TypeScriptParser
      parser.expressionParser.parseExpression()

      if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "then") {
        builder.remapCurrentToken(SvelteTokenTypes.THEN_KEYWORD)
        builder.advanceLexer()

        parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, true, false)
      }

      if (builder.tokenType === SvelteTokenTypes.CATCH_KEYWORD) {
        builder.advanceLexer()
        parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, true, false)
      }
    }
  }

  val AWAIT_START = createJS("AWAIT_START", AWAIT)
  val AWAIT_START_TS = createTS("AWAIT_START", AWAIT)

  private object THEN : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.COLON
      SvelteTagParsing.parseNotAllowedWhitespace(builder, ":")
      builder.remapCurrentToken(SvelteTokenTypes.THEN_KEYWORD)
      builder.advanceLexer() // JSTokenTypes.IDENTIFIER -- fake THEN_KEYWORD

      // TODO Check weird RBRACE placement
      parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, false, false)
    }
  }

  val THEN_CLAUSE = createJS("THEN_CLAUSE", THEN)
  val THEN_CLAUSE_TS = createTS("THEN_CLAUSE", THEN)

  private object CATCH : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.COLON
      SvelteTagParsing.parseNotAllowedWhitespace(builder, ":")
      builder.advanceLexer() // SvelteTokenTypes.CATCH_KEYWORD

      parser.expressionParser.parseDestructuringElement(SvelteJSElementTypes.PARAMETER, false, false)
    }
  }

  val CATCH_CLAUSE = createJS("CATCH_CLAUSE", CATCH)
  val CATCH_CLAUSE_TS = createTS("CATCH_CLAUSE", CATCH)

  private object KEY : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.SHARP
      SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
      builder.advanceLexer() // SvelteTokenTypes.KEY_KEYWORD

      parser.expressionParser.parseExpression()
    }
  }

  val KEY_START = createJS("KEY_START", KEY)
  val KEY_START_TS = createTS("KEY_START", KEY)


  private object SNIPPET : SvelteBlockLazyElementType {
    override val noTokensErrorMessage = "expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
      builder.advanceLexer() // JSTokenTypes.SHARP
      SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
      builder.advanceLexer() // SvelteTokenTypes.SNIPPET_KEYWORD

      try {
        builder.putUserData(markupContextKey, true)
        builder.putUserData(FunctionParser.methodsEmptinessKey, FunctionParser.MethodEmptiness.ALWAYS)
        val mark = builder.mark()
        parser.functionParser.parseFunctionNoMarker(FunctionParser.Context.SOURCE_ELEMENT, mark)
      } finally {
        builder.putUserData(FunctionParser.methodsEmptinessKey, null)
        builder.putUserData(markupContextKey, null)
      }
    }
  }

  val SNIPPET_START = createJS("SNIPPET_START", SNIPPET)
  val SNIPPET_START_TS = createTS("SNIPPET_START", SNIPPET)

  val TAG_DEPENDENT_EXPRESSION = SvelteJSElementType("TAG_DEPENDENT_EXPRESSION")

  val IF_END = SvelteJSElementType("IF_END")
  val EACH_END = SvelteJSElementType("EACH_END")
  val AWAIT_END = SvelteJSElementType("AWAIT_END")
  val KEY_END = SvelteJSElementType("KEY_END")
  val SNIPPET_END = SvelteJSElementType("SNIPPET_END")

  val START_TAGS = TokenSet.create(
    IF_START, IF_START_TS,
    EACH_START, EACH_START_TS,
    AWAIT_START, AWAIT_START_TS,
    KEY_START, KEY_START_TS,
    SNIPPET_START, SNIPPET_START_TS
  )
  val INNER_TAGS = TokenSet.create(
    ELSE_CLAUSE, ELSE_CLAUSE_TS,
    THEN_CLAUSE, THEN_CLAUSE_TS,
    CATCH_CLAUSE, CATCH_CLAUSE_TS
  )
  val END_TAGS = TokenSet.create(IF_END, EACH_END, AWAIT_END, KEY_END, SNIPPET_END)
  val INITIAL_TAGS = TokenSet.orSet(START_TAGS, INNER_TAGS)
  val TAIL_TAGS = TokenSet.orSet(INNER_TAGS, END_TAGS)
}


private fun createJS(debugName: String, impl: SvelteBlockLazyElementType): SvelteJSBlockLazyElementType {
  return create(debugName, impl, SvelteLanguageMode.Javascript)
}

private fun createTS(debugName: String, impl: SvelteBlockLazyElementType): SvelteJSBlockLazyElementType {
  return create("${debugName}_TS", impl, SvelteLanguageMode.TypeScript)
}

private fun create(
  debugName: String,
  impl: SvelteBlockLazyElementType,
  mode: SvelteLanguageMode,
): SvelteJSBlockLazyElementType {
  return object : SvelteJSBlockLazyElementType(debugName, mode) {
    override val noTokensErrorMessage: String
      get() = impl.noTokensErrorMessage

    override fun parseTokens(
      builder: PsiBuilder,
      parser: JavaScriptParser
    ) {
      impl.parseTokens(builder, parser)
    }
  }
}


