package dev.blachut.svelte.lang.parsing.ts

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.javascript.dialects.TypeScriptParserDefinition
import com.intellij.lang.javascript.parsing.JavaScriptParser
import com.intellij.lang.javascript.types.JSFileElementType
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IFileElementType
import dev.blachut.svelte.lang.SvelteLanguage
import dev.blachut.svelte.lang.SvelteLanguageMode
import dev.blachut.svelte.lang.SvelteTypeScriptLanguage

val SVELTETS_FILE: IFileElementType = object  : JSFileElementType(SvelteTypeScriptLanguage.INSTANCE) {
  init {
//    language.putUserDataIfAbsent(TYPE, type);
  }

  override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode? {
    psi.containingFile.putUserData(SvelteLanguage.LANG_MODE, SvelteLanguageMode.TypeScript)
    return super.doParseContents(chameleon, psi)
  }

}

class SvelteTypeScriptParserDefinition : TypeScriptParserDefinition() {
  override fun getFileNodeType(): IFileElementType {
    return SVELTETS_FILE
  }

  override fun createJSParser(builder: PsiBuilder): JavaScriptParser {
    return SvelteTypeScriptParser(builder)
  }
}
