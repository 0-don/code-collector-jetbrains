package don.codecollector.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
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
            resolveInternal(importPath, contextFile, project)
        }

    private fun resolveInternal(
        importPath: String,
        contextFile: VirtualFile,
        project: Project,
    ): VirtualFile? {
        try {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val fileIndex = ProjectFileIndex.getInstance(project)

            // Get the module for context
            val contextModule = fileIndex.getModuleForFile(contextFile)

            // Create appropriate search scope
            val searchScope =
                contextModule?.let { module ->
                    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                } ?: GlobalSearchScope.projectScope(project)

            val psiClass = psiFacade.findClass(importPath, searchScope)

            return psiClass?.containingFile?.virtualFile?.let { resolvedFile ->
                // Only return if it's a project file (not library)
                if (fileIndex.isInProject(resolvedFile)) resolvedFile else null
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun clearCache() {
        resolveCache.clear()
    }
}
