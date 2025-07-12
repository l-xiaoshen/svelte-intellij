package dev.blachut.svelte.lang.psi

import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.lang.javascript.types.TypeScriptAsExpressionElementType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.parsing.html.SvelteTagParsing
import dev.blachut.svelte.lang.psi.SvelteTagElementTypes.TAG_DEPENDENT_EXPRESSION
import java.util.*


private data class UnknownStateError(override val message: String) : Exception(message)

private data class MarkerPair(val restore: PsiBuilder.Marker, val marker: PsiBuilder.Marker) {
  init {
    require(restore != marker)
  }

  fun drop() {
    marker.drop()
    restore.drop()
    // restore comes ealier
  }
}

private val endingSet = TokenSet.create(SvelteTokenTypes.END_MUSTACHE, JSTokenTypes.COMMA, JSTokenTypes.LPAR)

// things after `,` or end `}` is seperated from the main parse

// state: the current parsing state. Before, not after
private sealed interface EachBlockParsingState<out T> {


  fun parse(
    builder: PsiBuilder,
    parser: JavaScriptParser,
    markers: NestedMarker,
    mode: SvelteLanguageMode
  ): T

  fun nextState(
    builder: PsiBuilder,
    markers: NestedMarker,
    mode: SvelteLanguageMode,
    r: @UnsafeVariance T
  ): EachBlockParsingState<*>


  data object Expr : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      val m = builder.mark()
      val hidden = builder.mark()
      markers.push(MarkerPair(hidden, m))
      parser.expressionParser.parsePrimaryExpression()
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      if (endingSet.contains(builder.tokenType)) {
        return BaseEnd
      }

      if (builder.tokenType === SvelteTokenTypes.AS_KEYWORD) {
        return As
      }

      markers.pop().drop()
      throw UnknownStateError("unexpected token ${builder.tokenText}")
    }
  }


  data object As : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      val restore = builder.mark()
      builder.remapCurrentToken(SvelteTokenTypes.AS_KEYWORD)
      builder.advanceLexer() // AS
      val marker = builder.mark()
      markers.push(MarkerPair(restore, marker))
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      return Item
    }
  }

  data object ASType : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      builder.remapCurrentToken(SvelteTokenTypes.AS_KEYWORD)
      builder.advanceLexer() // AS
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      return Type
    }
  }


  data object Type : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {

      val m = markers.pop()
      m.restore.drop()


      val marker = m.marker.precede()
      val restore = marker.precede()


      markers.push(MarkerPair(restore, marker))

      if (!parser.typeParser.parseType()) {
        m.marker.drop()
        markers.pop().drop()
        throw UnknownStateError("Type parsing failed")
      }


      m.marker.done(TypeScriptAsExpressionElementType())
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      if (builder.tokenType == SvelteTokenTypes.AS_KEYWORD) {
        return As
      }

      throw UnknownStateError("expect as keyword and item expression")
    }

  }


  data object Item : EachBlockParsingState<IElementType> {


    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker,
      mode: SvelteLanguageMode
    ): IElementType {
      return parser.expressionParser.parseDestructuringElementNoMarker(SvelteJSElementTypes.PARAMETER, false, false)
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: IElementType
    ): EachBlockParsingState<*> {
      return if (endingSet.contains(builder.tokenType)) {
        val (restore, marker) = markers.pop()

        restore.drop()
        marker.done(r)

        BaseEnd
      } else if (mode == SvelteLanguageMode.TypeScript) {

        val (restore, marker) = markers.pop()
        marker.drop()
        restore.rollbackTo()
        ASType
      } else {
        markers.pop().drop()
        throw UnknownStateError("unexpected token ${builder.tokenText}")
      }
    }
  }

  data object BaseEnd : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      val (restore, marker) = markers.pop()
      marker.drop()
      restore.drop()
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      if (builder.tokenType == JSTokenTypes.COMMA) {
        builder.advanceLexer()

        if (builder.tokenType != JSTokenTypes.IDENTIFIER) {
          throw UnknownStateError("unexpect a variable identifier for index")
        }

        return Index
      }

      if (builder.tokenType == JSTokenTypes.LPAR) {
        return Key
      }

      if (builder.tokenType == SvelteTokenTypes.END_MUSTACHE) {
        builder.advanceLexer()
        return STOP
      }

      throw UnknownStateError("unexpected token ${builder.tokenText}")
    }
  }

  data object Index : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      val m = builder.mark()
      builder.advanceLexer()
      m.done(SvelteJSElementTypes.PARAMETER)
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {

      if (builder.tokenType == JSTokenTypes.LPAR) {
        return Key
      }

      if (builder.tokenType == SvelteTokenTypes.END_MUSTACHE) {
        builder.advanceLexer()
        return STOP
      }

      throw UnknownStateError("unexpected token ${builder.tokenText}")
    }

  }


  data object Key : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      val m = builder.mark()
      parser.expressionParser.parseParenthesizedExpression()
      m.done(TAG_DEPENDENT_EXPRESSION)
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      if (builder.tokenType == SvelteTokenTypes.END_MUSTACHE) {
        builder.advanceLexer()
        return STOP
      }

      throw UnknownStateError("Expect `}`, unexpected token ${builder.tokenText}")
    }
  }

  data object STOP : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker, mode: SvelteLanguageMode) {
      error("Should not be called")
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      mode: SvelteLanguageMode,
      r: Unit
    ): EachBlockParsingState<*> {
      error("Should not be called")
    }
  }
}

private typealias NestedMarker = Stack<MarkerPair>

object EACHParsing : SvelteBlockLazyElementType {
  override val noTokensErrorMessage = "expression expected"

  override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode) {
    builder.advanceLexer() // JSTokenTypes.SHARP
    SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
    builder.advanceLexer() // SvelteTokenTypes.AWAIT_KEYWORD

    builder.setDebugMode(true)

    var state: EachBlockParsingState<*> = EachBlockParsingState.Expr

    val maxDepth = 1000
    var depth = 0

    val markers: NestedMarker = Stack()

    while (depth < maxDepth) {
      depth++

      try {
        val r = state.parse(builder, parser, markers, mode)
        state = state.nextState(builder, markers, mode, r)

        if (state == EachBlockParsingState.STOP) {
          return
        }

      } catch (e: UnknownStateError) {

        while (markers.isNotEmpty()) {
          markers.pop().drop()
        }

        builder.error(e.message)
        return
      }
    }

    while (markers.isNotEmpty()) {
      markers.pop().drop()
    }

    builder.error("Each block is too long.")

  }
}