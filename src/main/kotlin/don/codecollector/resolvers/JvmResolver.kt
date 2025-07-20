package don.codecollector.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class JvmResolver {
    private val resolveCache = mutableMapOf<String, VirtualFile?>()

    fun resolve(
        importPath: String,
        contextFile: VirtualFile,
        project: Project,
    ): VirtualFile? =
        resolveCache.getOrPut(importPath) {
            resolveInternal(importPath, project)
        }

    private fun resolveInternal(
        importPath: String,
        project: Project,
    ): VirtualFile? =
        try {
            val psiFacade = JavaPsiFacade.getInstance(project)

            // Try project scope first, then all scope - K2 compatible approach
            val psiClass =
                psiFacade.findClass(importPath, GlobalSearchScope.projectScope(project))
                    ?: psiFacade.findClass(importPath, GlobalSearchScope.allScope(project))

            psiClass?.containingFile?.virtualFile
        } catch (e: Exception) {
            // Handle any K2-related resolution issues gracefully
            null
        }

    fun clearCache() {
        resolveCache.clear()
    }
}
