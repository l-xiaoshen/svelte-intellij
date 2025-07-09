package dev.blachut.svelte.lang

import com.intellij.lang.javascript.JSLanguageDialect
import com.intellij.openapi.util.Key

object SvelteLanguage {
  val LANG_MODE = Key.create<SvelteLanguageMode>("svelte.lang.mode")
}

enum class SvelteLanguageMode(val language: JSLanguageDialect) {
  Javascript(SvelteJSLanguage.INSTANCE),
  TypeScript(SvelteTypeScriptLanguage.INSTANCE),
}