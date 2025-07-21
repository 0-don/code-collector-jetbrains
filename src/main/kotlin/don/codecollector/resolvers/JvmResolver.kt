package don.codecollector.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.roots.ModuleRootManager
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

            val contextModule = fileIndex.getModuleForFile(contextFile)
            val searchScope =
                contextModule?.let { module ->
                    GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)
                } ?: GlobalSearchScope.projectScope(project)

            val psiClass = psiFacade.findClass(importPath, searchScope)

            return psiClass?.containingFile?.virtualFile?.let { resolvedFile ->
                if (isOfficialSourceFile(resolvedFile, project)) {
                    resolvedFile
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun isOfficialSourceFile(
        file: VirtualFile,
        project: Project,
    ): Boolean {
        val fileIndex = ProjectFileIndex.getInstance(project)

        if (!fileIndex.isInProject(file)) return false
        if (!fileIndex.isInSource(file)) return false
        if (fileIndex.isInGeneratedSources(file)) return false
        if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(file, project)) return false

        val sourceRoot = fileIndex.getSourceRootForFile(file) ?: return false

        // Simple heuristic: if the source root contains "target" or "build" in its path,
        // and there are other source roots available, prefer the others
        val sourceRootPath = sourceRoot.path
        val module = fileIndex.getModuleForFile(file) ?: return false
        val allSourceRoots = ModuleRootManager.getInstance(module).getSourceRoots(false)

        if (allSourceRoots.size > 1) {
            // If there are multiple source roots, exclude build/target directories
            val isBuildDirectory =
                sourceRootPath.contains("/target/") || sourceRootPath.contains("/build/")
            if (isBuildDirectory) {
                return false
            }
        }

        return true
    }

    fun clearCache() {
        resolveCache.clear()
    }
}
