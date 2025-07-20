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
    ): VirtualFile? {
        // Use caching for better performance
        return resolveCache.getOrPut(importPath) {
            resolveInternal(importPath, project)
        }
    }

    private fun resolveInternal(
        importPath: String,
        project: Project,
    ): VirtualFile? {
        // Try to find the class using PSI - K2 compatible
        val psiFacade = JavaPsiFacade.getInstance(project)

        // Try project scope first, then all scope
        val psiClass =
            psiFacade.findClass(importPath, GlobalSearchScope.projectScope(project))
                ?: psiFacade.findClass(importPath, GlobalSearchScope.allScope(project))

        return psiClass?.containingFile?.virtualFile
    }

    fun clearCache() {
        resolveCache.clear()
    }
}
