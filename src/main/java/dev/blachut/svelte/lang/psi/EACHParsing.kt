package dev.blachut.svelte.lang.psi

import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.psi.tree.IElementType
import dev.blachut.svelte.lang.parsing.html.SvelteTagParsing
import java.util.*


private data class UnknownStateError(override val message: String) : Exception(message)

private data class MarkerPair(val restore: PsiBuilder.Marker, val marker: PsiBuilder.Marker)

// things after `,` or end `}` is seperated from the main parse

// state: the current parsing state. Before, not after
private sealed interface EachBlockParsingState<out T> {


  fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker): T

  fun nextState(builder: PsiBuilder, markers: NestedMarker, r: @UnsafeVariance T): EachBlockParsingState<*>


  data object Expr : EachBlockParsingState<Unit> {
    override fun parse(builder: PsiBuilder, parser: JavaScriptParser, markers: NestedMarker) {
      parser.expressionParser.parsePrimaryExpression()
    }

    override fun nextState(builder: PsiBuilder, markers: NestedMarker, r: Unit): EachBlockParsingState<*> {
      if (builder.tokenType === SvelteTokenTypes.END_MUSTACHE) {
        return End
      }

      if (builder.tokenType === SvelteTokenTypes.AS_KEYWORD) {
        return As
      }

      throw UnknownStateError("unexpected token ${builder.tokenText}")
    }
  }


  data object As : EachBlockParsingState<Unit> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ) {
      val restore = builder.mark()
      builder.remapCurrentToken(SvelteTokenTypes.AS_KEYWORD)
      builder.advanceLexer() // AS
      val marker = builder.mark()
      markers.push(MarkerPair(restore, marker))
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      r: Unit
    ): EachBlockParsingState<*> {
      return Item
    }
  }

  data object ASType : EachBlockParsingState<Unit> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ) {
      builder.remapCurrentToken(SvelteTokenTypes.AS_KEYWORD)
      builder.advanceLexer() // AS
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      r: Unit
    ): EachBlockParsingState<*> {
      return Type
    }
  }


  data object Type : EachBlockParsingState<Unit> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ) {

      println("marks on stack:  ${markers.size}")

      parser.typeParser.parseType()
    }

    override fun nextState(
      builder: PsiBuilder,
      markers: NestedMarker,
      r: Unit
    ): EachBlockParsingState<*> {
      return As
    }

  }

  data object Item : EachBlockParsingState<IElementType> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ): IElementType {
      return parser.expressionParser.parseDestructuringElementNoMarker(SvelteJSElementTypes.PARAMETER, false, false)
    }

    override fun nextState(builder: PsiBuilder, markers: NestedMarker, r: IElementType): EachBlockParsingState<*> {
      return if (builder.tokenType === SvelteTokenTypes.END_MUSTACHE) {
        val (restore, marker) = markers.pop()

        restore.drop()
        marker.done(r)

        End
      } else {
        markers.pop().restore.rollbackTo()
        ASType
      }
    }
  }

  //  data object Type: EachBlockParsingState
  data object End : EachBlockParsingState<Unit> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ) {
      if (builder.tokenType == SvelteTokenTypes.END_MUSTACHE) {

        println("Size after all: ${markers.size}")

        builder.advanceLexer()
        return
      }
    }

    override fun nextState(builder: PsiBuilder, markers: NestedMarker, r: Unit): EachBlockParsingState<*> {
      return STOP
    }
  }

  data object STOP : EachBlockParsingState<Unit> {
    override fun parse(
      builder: PsiBuilder,
      parser: JavaScriptParser,
      markers: NestedMarker
    ) {
      error("Should not be called")
    }

    override fun nextState(builder: PsiBuilder, markers: NestedMarker, r: Unit): EachBlockParsingState<*> {
      error("Should not be called")
    }

  }
}

private typealias NestedMarker = Stack<MarkerPair>

object EACHParsing : SvelteBlockLazyElementType {
  override val noTokensErrorMessage = "expression expected"

  override fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser) {
    builder.advanceLexer() // JSTokenTypes.SHARP
    SvelteTagParsing.parseNotAllowedWhitespace(builder, "#")
    builder.advanceLexer() // SvelteTokenTypes.AWAIT_KEYWORD

    builder.setDebugMode(true)

    var state: EachBlockParsingState<*> = EachBlockParsingState.Expr

    val maxDepth = 100000
    var depth = 0

    val markers: NestedMarker = Stack()

    while (depth < maxDepth) {
      depth++

      try {
        val r = state.parse(builder, parser, markers)
        state = state.nextState(builder, markers, r)

        if (state == EachBlockParsingState.STOP) {
          break
        }

      } catch (e: UnknownStateError) {
        builder.error(e.message)
        break
      }
    }

    println("Remaining marks: ${markers.size}")
//    markers.pop()

  }
}