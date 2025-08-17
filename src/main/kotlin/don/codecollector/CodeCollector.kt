package don.codecollector

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import don.codecollector.parsers.JavaKotlinParser
import don.codecollector.resolvers.JvmResolver
import don.codecollector.settings.CodeCollectorSettings
import java.util.regex.Pattern

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
        val allFilePaths = mutableSetOf<String>()

        resolver.clearCache()

        val allFiles = mutableListOf<VirtualFile>()

        files.forEach { file ->
            if (file.isDirectory) {
                collectFilesFromDirectory(file, project)
                    .forEach { dirFile ->
                        if (allFilePaths.add(dirFile.path)) {
                            allFiles.add(dirFile)
                        }
                    }
            } else {
                if (allFilePaths.add(file.path)) {
                    allFiles.add(file)
                }
            }
        }

        allFiles.forEach { file ->
            if (isJavaKotlin(file, project)) {
                processFile(file, project, contexts, processed)
            } else if (isValidFile(file)) {
                addFileContext(file, project, contexts)
            }
        }

        return contexts
    }

    private fun collectFilesFromDirectory(
        directory: VirtualFile,
        project: Project,
    ): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val queue = ArrayDeque<VirtualFile>()
        queue.add(directory)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            try {
                current.children?.forEach { file ->
                    if (file.isDirectory) {
                        queue.add(file)
                    } else if (isValidFile(file) &&
                        !shouldIgnore(file, project)
                    ) {
                        files.add(file)
                    }
                }
            } catch (_: Exception) {
                // Skip inaccessible directories
            }
        }

        return files
    }

    private fun isJavaKotlin(
        file: VirtualFile,
        project: Project,
    ): Boolean =
        file.extension in setOf("java", "kt") &&
            file.isValid &&
            file.exists() &&
            isOfficialSourceFile(file, project)

    private fun isValidFile(file: VirtualFile): Boolean =
        file.isValid &&
            file.exists() &&
            !file.isDirectory

    fun collectAllFiles(project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processedPaths = mutableSetOf<String>()

        // Collect from project root (includes files like pom.xml)
        project.basePath?.let { basePath ->
            val projectRoot = VfsUtil.findFileByIoFile(java.io.File(basePath), true)
            projectRoot?.let { root ->
                collectFromDirectory(root, project, contexts, processedPaths)
            }
        }

        return contexts
    }

    fun collectSelectedFiles(
        files: List<VirtualFile>,
        project: Project,
    ): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processed = mutableSetOf<String>()

        files.forEach { file ->
            if (file.isDirectory) {
                // Collect all files from directory without import analysis
                collectFilesFromDirectory(file, project)
                    .forEach { dirFile ->
                        if (processed.add(dirFile.path) && isValidFile(dirFile) &&
                            !shouldIgnore(
                                dirFile,
                                project,
                            )
                        ) {
                            addSimpleFileContext(dirFile, project, contexts)
                        }
                    }
            } else {
                // Add individual file without import analysis
                if (processed.add(file.path) && isValidFile(file) && !shouldIgnore(file, project)) {
                    addSimpleFileContext(file, project, contexts)
                }
            }
        }

        return contexts
    }

    private fun addSimpleFileContext(
        file: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
    ) {
        try {
            val content = String(file.contentsToByteArray())
            val relativePath = getRelativePath(file, project)
            contexts.add(FileContext(file.path, content, relativePath))
        } catch (_: Exception) {
            // Skip files that can't be read
        }
    }

    private fun collectFromDirectory(
        directory: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
        processedPaths: MutableSet<String>,
    ) {
        if (!directory.isValid || !directory.exists()) return

        val queue = ArrayDeque<VirtualFile>()
        queue.add(directory)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            try {
                current.children?.forEach { file ->
                    if (processedPaths.contains(file.path)) return@forEach

                    if (file.isDirectory) {
                        queue.add(file)
                    } else if (isValidFile(file) &&
                        !shouldIgnore(file, project)
                    ) {
                        try {
                            val relativePath = getRelativePath(file, project)
                            val content = String(file.contentsToByteArray())
                            contexts.add(FileContext(file.path, content, relativePath))
                            processedPaths.add(file.path)
                        } catch (_: Exception) {
                            // Skip files that can't be read
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip directories that can't be accessed
            }
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

        addFileContext(file, project, contexts)?.let { psiFile ->
            parser.parseImports(psiFile).forEach { importInfo ->
                resolver.resolve(importInfo.module, file, project)?.let { resolved ->
                    processFile(resolved, project, contexts, processed)
                }
            }
        }
    }

    private fun addFileContext(
        file: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
    ): PsiFile? =
        try {
            val content = String(file.contentsToByteArray())
            val relativePath = getRelativePath(file, project)
            contexts.add(FileContext(file.path, content, relativePath))
            PsiManager.getInstance(project).findFile(file)
        } catch (_: Exception) {
            null
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
    ): Boolean =
        file.extension in setOf("java", "kt") &&
            file.isValid &&
            file.exists() &&
            isOfficialSourceFile(file, project)

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

    private fun shouldIgnore(
        file: VirtualFile,
        project: Project,
    ): Boolean {
        val settings = CodeCollectorSettings.getInstance(project)
        val enabledPatterns = settings.getEnabledPatterns() // Use only enabled patterns
        val relativePath = FileUtil.toSystemIndependentName(getRelativePath(file, project))
        return enabledPatterns.any { pattern ->
            try {
                val regexPattern = FileUtil.convertAntToRegexp(pattern)
                Pattern.compile(regexPattern).matcher(relativePath).matches()
            } catch (_: Exception) {
                false // Skip invalid patterns
            }
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
