package don.codecollector

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import don.codecollector.parsers.JavaKotlinParser
import don.codecollector.resolvers.JvmResolver

data class FileContext(
    val path: String,
    val content: String,
    val relativePath: String,
)

class ContextCollector {
    private val parser = JavaKotlinParser()
    private val resolver = JvmResolver()

    fun collectFromFiles(
        files: List<VirtualFile>,
        project: Project,
    ): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processed = mutableSetOf<String>()

        // Clear resolver cache for fresh analysis
        resolver.clearCache()

        files.filter { isSupported(it, project) }.forEach { file ->
            processFile(file, project, contexts, processed)
        }

        return contexts
    }

    fun collectAllFiles(project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val moduleManager = ModuleManager.getInstance(project)

        // Process each module
        moduleManager.modules.forEach { module ->
            val moduleRootManager = ModuleRootManager.getInstance(module)

            // Get only source roots (exclude test sources)
            moduleRootManager.getSourceRoots(false).forEach { sourceRoot ->
                collectFromSourceRoot(sourceRoot, project, contexts)
            }
        }

        return contexts
    }

    private fun collectFromSourceRoot(
        sourceRoot: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
    ) {
        if (!sourceRoot.isValid || !sourceRoot.exists()) return

        try {
            sourceRoot.children?.forEach { file ->
                if (file.isDirectory) {
                    collectFromSourceRoot(file, project, contexts)
                } else if (isSupported(file, project)) {
                    try {
                        val relativePath = getRelativePath(file, project)
                        val content = String(file.contentsToByteArray())
                        contexts.add(FileContext(file.path, content, relativePath))
                    } catch (e: Exception) {
                        // Skip files that can't be read
                    }
                }
            }
        } catch (e: Exception) {
            // Skip directories that can't be accessed
        }
    }

    private fun processFile(
        file: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
        processed: MutableSet<String>,
    ) {
        if (file.path in processed || !isSupported(file, project)) return

        processed.add(file.path)

        try {
            val content = String(file.contentsToByteArray())
            val relativePath = getRelativePath(file, project)

            contexts.add(FileContext(file.path, content, relativePath))

            // Parse imports and resolve dependencies
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
            val imports = parser.parseImports(psiFile)

            imports.forEach { importInfo ->
                val resolvedFile = resolver.resolve(importInfo.module, file, project)
                resolvedFile?.let { resolved ->
                    processFile(resolved, project, contexts, processed)
                }
            }
        } catch (e: Exception) {
            // Skip files that cause errors
        }
    }

    fun formatContexts(contexts: List<FileContext>): String {
        var currentLine = 1
        val output = StringBuilder()

        contexts.forEach { context ->
            val lines = context.content.lines()
            val endLine = currentLine + lines.size - 1
            output.append("\n// ${context.relativePath} (L$currentLine-L$endLine)\n")
            output.append(context.content)
            output.append("\n")
            currentLine = endLine + 1
        }

        return output.toString()
    }

    private fun isSupported(
        file: VirtualFile,
        project: Project,
    ): Boolean {
        if (file.extension !in setOf("java", "kt")) return false
        if (!file.isValid || !file.exists()) return false

        return isOfficialSourceFile(file, project)
    }

    private fun isOfficialSourceFile(
        file: VirtualFile,
        project: Project,
    ): Boolean {
        val fileIndex = ProjectFileIndex.getInstance(project)

        if (!fileIndex.isInProject(file)) return false
        if (!fileIndex.isInSource(file)) return false
        if (fileIndex.isInGeneratedSources(file)) return false

        val module = fileIndex.getModuleForFile(file) ?: return false
        val moduleRootManager = ModuleRootManager.getInstance(module)

        val sourceRoots = moduleRootManager.getSourceRoots(false)
        return sourceRoots.any { sourceRoot ->
            VfsUtil.isAncestor(sourceRoot, file, false)
        }
    }

    private fun getRelativePath(
        file: VirtualFile,
        project: Project,
    ): String {
        val basePath = project.basePath ?: return file.name
        return file.path.removePrefix("$basePath/")
    }
}
