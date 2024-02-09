// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package dev.blachut.svelte.lang.actions

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.impl.CustomFileTemplate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import dev.blachut.svelte.lang.SvelteBundle
import dev.blachut.svelte.lang.hasSvelteFiles
import dev.blachut.svelte.lang.isSvelteContext
import dev.blachut.svelte.lang.isSvelteProjectContext
import icons.SvelteIcons

class SvelteCreateComponentAction : CreateFileFromTemplateAction(), DumbAware {
  companion object {
    private const val TEMPLATE_NAME: String = "Svelte Component"
  }

  override fun isAvailable(dataContext: DataContext): Boolean =
    super.isAvailable(dataContext)
    && (PlatformDataKeys.PROJECT.getData(dataContext)?.let { hasSvelteFiles(it) } == true
        || (PlatformDataKeys.PSI_ELEMENT.getData(dataContext)
            ?: PlatformDataKeys.PSI_FILE.getData(dataContext))?.let { isSvelteProjectContext(it) } == true)

  override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
    val name = SvelteBundle.message("svelte.component")
    builder
      .setTitle(SvelteBundle.message("svelte.create.component.title", name))
      .addKind(name, SvelteIcons.Desaturated, TEMPLATE_NAME)
  }

  override fun getActionName(directory: PsiDirectory?, @NlsSafe newName: String, templateName: String?): String =
    SvelteBundle.message("svelte.new.component.create", SvelteBundle.message("svelte.component"), newName)

  override fun createFile(name: String, templateName: String?, dir: PsiDirectory?): PsiFile? {
    val template = CustomFileTemplate(name, "svelte")
    return createFileFromTemplate(name, template, dir)
  }
}
