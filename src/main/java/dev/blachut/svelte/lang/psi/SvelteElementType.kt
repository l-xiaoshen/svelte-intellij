package dev.blachut.svelte.lang.psi

import com.intellij.psi.tree.IElementType
import dev.blachut.svelte.lang.SvelteHTMLLanguage
import dev.blachut.svelte.lang.SvelteJSLanguage
import dev.blachut.svelte.lang.SvelteTypeScriptLanguage

class SvelteElementType(debugName: String) : IElementType(debugName, SvelteJSLanguage.INSTANCE)
