package dev.blachut.svelte.lang.parsing.html

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.ParsingDiagnostics
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.xml.HtmlLanguageStubVersionUtil
import dev.blachut.svelte.lang.SvelteHTMLLanguage
import dev.blachut.svelte.lang.SvelteLanguage
import dev.blachut.svelte.lang.psi.SvelteJSElementTypes

// based on HtmlFileElementType
class SvelteHtmlFileElementType : IStubFileElementType<PsiFileStub<*>>("svelte file", SvelteHTMLLanguage.INSTANCE) {
  override fun getStubVersion(): Int {
    return HtmlLanguageStubVersionUtil.getHtmlStubVersion() + SvelteJSElementTypes.STUB_VERSION
  }

  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    val languageForParser = getLanguageForParser(psi)

    val project = psi.project
    val lexer = SvelteHtmlLexer(false)
    val builder =
      PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, languageForParser, chameleon.chars)

    val mode = lexer.langMode
//    val mode = SvelteLanguageMode.TypeScript
    psi.containingFile.putUserData(SvelteLanguage.LANG_MODE, mode)
    builder.putUserData(SvelteLanguage.LANG_MODE, mode)


    val parser = LanguageParserDefinitions.INSTANCE.forLanguage(languageForParser)!!.createParser(project)
    val startTime = System.nanoTime()
    val node = parser.parse(this, builder)
    ParsingDiagnostics.registerParse(builder, languageForParser, System.nanoTime() - startTime)

    return node.firstChildNode
  }


  companion object {
    val FILE = SvelteHtmlFileElementType()
  }
}