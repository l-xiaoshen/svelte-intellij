package dev.blachut.svelte.lang.psi

import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.JSStubElementTypes
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.lang.javascript.parsing.JavaScriptParserBase
import dev.blachut.svelte.lang.SvelteBundle
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.directives.createDualIElementType
import dev.blachut.svelte.lang.isTokenAfterWhiteSpace

object SvelteJSLazyElementTypes {
  private object ATTRIBUTE_PARAMETER_IMPL : SvelteLazyElementType {
    override val noTokensErrorMessage = "Parameter expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode) {
      parseAtModifiersError(builder)
      parser.expressionParser.parseDestructuringElement(
        SvelteJSElementTypes.PARAMETER,
        mode == SvelteLanguageMode.TypeScript,
        false
      )
    }
  }

  private val ATTRIBUTE_PARAMETER_JS = createJS("ATTRIBUTE_PARAMETER", ATTRIBUTE_PARAMETER_IMPL)
  private val ATTRIBUTE_PARAMETER_TS = createTS("ATTRIBUTE_PARAMETER", ATTRIBUTE_PARAMETER_IMPL)
  val ATTRIBUTE_PARAMETER = createDualIElementType(ATTRIBUTE_PARAMETER_JS, ATTRIBUTE_PARAMETER_TS)

  private object ATTRIBUTE_EXPRESSION_IMPL : SvelteLazyElementType {
    override val noTokensErrorMessage = "Expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode) {
      parseAtModifiersError(builder)
      parser.expressionParser.parseExpression()
    }
  }

  private val ATTRIBUTE_EXPRESSION_JS = createJS("ATTRIBUTE_EXPRESSION", ATTRIBUTE_EXPRESSION_IMPL)
  private val ATTRIBUTE_EXPRESSION_TS = createTS("ATTRIBUTE_EXPRESSION", ATTRIBUTE_EXPRESSION_IMPL)
  val ATTRIBUTE_EXPRESSION = createDualIElementType(ATTRIBUTE_EXPRESSION_JS, ATTRIBUTE_EXPRESSION_TS)

  /**
   * Text expressions + html, debug & render + const
   */
  private object CONTENT_EXPRESSION_IMPL : SvelteLazyElementType {
    override val noTokensErrorMessage = "Expression expected"
    override val assumeExternalBraces = false // for now trailing { and } belong to this token

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode) {
      if (parseAtModifiers(builder)) {
        parseSvelteDeclaringAssignmentExpression(builder, parser, mode)
      } else {
        parser.expressionParser.parseExpression()
      }
    }

    private fun parseSvelteDeclaringAssignmentExpression(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      mode: SvelteLanguageMode
    ) {
      val expr: PsiBuilder.Marker = builder.mark()

      var openedPar = false
      if (builder.tokenType === JSTokenTypes.LPAR) {
        openedPar = true
        builder.advanceLexer()
      }


      parser.statementParser.parseVarDeclaration(
        SvelteTSElementTypes.CONST_TAG_VARIABLE,
        mode == SvelteLanguageMode.TypeScript,
        false
      )

      if (openedPar) {
        JavaScriptParserBase.checkMatches(builder, JSTokenTypes.RPAR, "javascript.parser.message.expected.rparen")
      }

      expr.done(JSStubElementTypes.VAR_STATEMENT)
    }
  }

  private val CONTENT_EXPRESSION_JS = createJS("CONTENT_EXPRESSION", CONTENT_EXPRESSION_IMPL)
  private val CONTENT_EXPRESSION_TS = createTS("CONTENT_EXPRESSION", CONTENT_EXPRESSION_IMPL)
  val CONTENT_EXPRESSION = createDualIElementType(CONTENT_EXPRESSION_JS, CONTENT_EXPRESSION_TS)

  private object SPREAD_OR_SHORTHAND_IMPL : SvelteLazyElementType {
    override val noTokensErrorMessage = "Shorthand attribute or spread expression expected"

    override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode) {
      parseAtModifiersError(builder)
      if (builder.tokenType === JSTokenTypes.DOT_DOT_DOT) {
        val marker = builder.mark()
        builder.advanceLexer()
        parseAtModifiersError(builder)
        parser.expressionParser.parseAssignmentExpression(false)
        marker.done(JSStubElementTypes.SPREAD_EXPRESSION)
      } else {
        parser.expressionParser.parseAssignmentExpression(false)
      }
    }
  }

  private val SPREAD_OR_SHORTHAND_JS = createJS("SPREAD_OR_SHORTHAND", SPREAD_OR_SHORTHAND_IMPL)
  private val SPREAD_OR_SHORTHAND_TS = createTS("SPREAD_OR_SHORTHAND", SPREAD_OR_SHORTHAND_IMPL)
  val SPREAD_OR_SHORTHAND = createDualIElementType(SPREAD_OR_SHORTHAND_JS, SPREAD_OR_SHORTHAND_TS)

  private fun parseAtModifiers(builder: PsiBuilder): Boolean {
    val unexpectedTokens = setOf(JSTokenTypes.SHARP, JSTokenTypes.COLON, JSTokenTypes.DIV)

    var constMode = false

    if (builder.tokenType === JSTokenTypes.AT) {
      builder.advanceLexer()

      if (builder.isTokenAfterWhiteSpace()) {
        builder.error(SvelteBundle.message("svelte.parsing.error.whitespace.not.allowed.after"))
      }

      if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "html") {
        builder.remapCurrentToken(SvelteTokenTypes.HTML_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "debug") {
        builder.remapCurrentToken(SvelteTokenTypes.DEBUG_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "render") {
        builder.remapCurrentToken(SvelteTokenTypes.RENDER_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === SvelteTokenTypes.CONST_KEYWORD) {
        constMode = true
        builder.advanceLexer()
      } else {
        val errorMarker = builder.mark()
        builder.advanceLexer()
        errorMarker.error(SvelteBundle.message("svelte.parsing.error.expected.html.debug.render.const"))
      }
    } else if (unexpectedTokens.contains(builder.tokenType)) {
      builder.advanceLexer()

      if (builder.isTokenAfterWhiteSpace()) {
        builder.error(SvelteBundle.message("svelte.parsing.error.whitespace.not.allowed.here"))
      }
      val errorMarker = builder.mark()
      builder.advanceLexer()
      errorMarker.error(SvelteBundle.message("svelte.parsing.error.invalid.block.name"))
    }

    return constMode
  }

  private fun parseAtModifiersError(builder: PsiBuilder) {
    if (builder.tokenType === JSTokenTypes.AT) {
      val errorMarker = builder.mark()
      builder.advanceLexer()

      // copied from parseAtModifiers above
      if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "html") {
        builder.remapCurrentToken(SvelteTokenTypes.HTML_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "debug") {
        builder.remapCurrentToken(SvelteTokenTypes.DEBUG_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "render") {
        builder.remapCurrentToken(SvelteTokenTypes.RENDER_KEYWORD)
        builder.advanceLexer()
      } else if (builder.tokenType === JSTokenTypes.IDENTIFIER && builder.tokenText == "const") {
        builder.remapCurrentToken(SvelteTokenTypes.CONST_KEYWORD)
        builder.advanceLexer()
      }

      errorMarker.error(SvelteBundle.message("svelte.parsing.error.modifiers.are.not.allowed.here"))
    }
  }
}

private fun createJS(debugName: String, impl: SvelteLazyElementType): SvelteJSLazyElementType {
  return create(debugName, impl, SvelteLanguageMode.Javascript)
}

private fun createTS(debugName: String, impl: SvelteLazyElementType): SvelteJSLazyElementType {
  return create("${debugName}_TS", impl, SvelteLanguageMode.TypeScript)
}

private fun create(
  debugName: String,
  impl: SvelteLazyElementType,
  mode: SvelteLanguageMode,
): SvelteJSLazyElementType {
  return object : SvelteJSLazyElementType(debugName, mode) {
    override val noTokensErrorMessage: String
      get() = impl.noTokensErrorMessage

    override val assumeExternalBraces: Boolean
      get() = impl.assumeExternalBraces


    override fun parseTokens(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      mode: SvelteLanguageMode
    ) {
      impl.parseTokens(builder, parser, mode)
    }
  }
}
