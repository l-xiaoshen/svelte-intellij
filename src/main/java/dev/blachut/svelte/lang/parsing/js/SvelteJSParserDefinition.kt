package dev.blachut.svelte.lang.parsing.js

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.dialects.ECMA6ParserDefinition
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.lang.javascript.types.JSFileElementType
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType
import dev.blachut.svelte.lang.SvelteJSLanguage
import dev.blachut.svelte.lang.SvelteLanguage
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.psi.SvelteElementTypes

val SVELTEJS_FILE: IFileElementType = object  : JSFileElementType(SvelteJSLanguage.INSTANCE) {
  init {
//    language.putUserDataIfAbsent(TYPE, type);
  }

  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    psi.containingFile.putUserData(SvelteLanguage.LANG_MODE, SvelteLanguageMode.Javascript)
    return super.doParseContents(chameleon, psi)
  }

}


class SvelteJSParserDefinition : ECMA6ParserDefinition() {
  override fun getFileNodeType(): IFileElementType {
    return SVELTEJS_FILE
  }

  override fun createJSParser(builder: PsiBuilder): JavaScriptParser {
    return SvelteJSParser(builder)
  }

  override fun createElement(node: ASTNode): PsiElement {
    return try {
      SvelteElementTypes.createElement(node)
    }
    catch (e: Exception) {
      super.createElement(node)
    }
  }
}
