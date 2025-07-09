package dev.blachut.svelte.lang.psi

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.javascript.JSLanguageUtil
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.psi.ParsingDiagnostics
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.ILazyParseableElementType
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.parsing.html.SvelteJSExpressionLexer

interface SvelteLazyElementType {
  val noTokensErrorMessage: String
  val assumeExternalBraces: Boolean get() = true


   fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode)

}


abstract class SvelteJSLazyElementType(debugName: String, val mode: SvelteLanguageMode) :
  ILazyParseableElementType(debugName, mode.language) {
  protected abstract val noTokensErrorMessage: String
  protected open val excessTokensErrorMessage = "Unexpected token"

  protected open val assumeExternalBraces: Boolean = true

  override fun createNode(text: CharSequence?): ASTNode? {
    text ?: return null
    return SvelteJSLazyPsiElement(this, text)
  }


  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
    val language = mode.language

    val project = psi.project
    val lexer = SvelteJSExpressionLexer(assumeExternalBraces)
    val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, language, chameleon.chars)
//    builder.putUserData(SvelteLanguage.LANG_MODE, mode)
    val startTime = System.nanoTime()
    val parser = JSLanguageUtil.createJSParser(language, builder)

    val rootMarker = builder.mark()



    if (builder.eof()) {
      builder.error(noTokensErrorMessage)
    } else {
      if (!assumeExternalBraces) {
        builder.remapCurrentToken(JSTokenTypes.LBRACE)
        builder.advanceLexer()
      }
      parseTokens(builder, parser, mode)
      if (!assumeExternalBraces) {
        if (builder.tokenType == SvelteTokenTypes.END_MUSTACHE) {
          builder.remapCurrentToken(JSTokenTypes.RBRACE)
        }
        builder.advanceLexer()
      }

      ensureEof(builder)
    }

    rootMarker.done(this)

    val result = builder.treeBuilt.firstChildNode
    ParsingDiagnostics.registerParse(builder, language, System.nanoTime() - startTime)
    return result
  }

  protected abstract fun parseTokens(builder: PsiBuilder, parser: JavaScriptParser, mode: SvelteLanguageMode)

  private fun ensureEof(builder: PsiBuilder) {
    if (!builder.eof()) {
      builder.error(excessTokensErrorMessage)
      // todo merge back into SvelteTagParsing.finishTag
      while (!builder.eof() && builder.tokenType !== SvelteTokenTypes.END_MUSTACHE) {
        builder.advanceLexer()
      }
      if (builder.tokenType === SvelteTokenTypes.END_MUSTACHE) {
        builder.remapCurrentToken(JSTokenTypes.RBRACE)
        builder.advanceLexer()
      }
    }
  }
}
