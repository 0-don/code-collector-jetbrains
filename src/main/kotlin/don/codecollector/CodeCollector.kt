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
import org.eclipse.jgit.ignore.IgnoreNode
import org.eclipse.jgit.ignore.IgnoreNode.MatchResult
import java.io.ByteArrayInputStream

data class FileContext(
    val path: String,
    val content: String,
    val relativePath: String,
)

enum class CollectionMode {
    SMART,    // Follow imports
    DIRECT,   // Selected files only
    ALL       // Entire project
}

class CodeCollector {
    private var ignoreNode: IgnoreNode? = null
    private var lastPatternsHash: Int = 0
    private val parser = JavaKotlinParser()
    private val resolver = JvmResolver()

    fun collect(
        files: List<VirtualFile>,
        project: Project,
        mode: CollectionMode
    ): List<FileContext> {
        resolver.clearCache()

        return when (mode) {
            CollectionMode.SMART -> collectSmart(files, project)
            CollectionMode.DIRECT -> collectDirect(files, project)
            CollectionMode.ALL -> collectAll(project)
        }
    }

    private fun collectSmart(files: List<VirtualFile>, project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processed = mutableSetOf<String>()

        val allFiles = expandDirectoriesWithIgnorePatterns(files, project)

        allFiles.forEach { file ->
            if (isJavaKotlin(file, project)) {
                processFileWithImports(file, project, contexts, processed)
            } else if (isValidFile(file)) {
                addFileContext(file, project, contexts)
            }
        }

        return contexts
    }

    private fun collectDirect(files: List<VirtualFile>, project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processed = mutableSetOf<String>()

        val allFiles = expandDirectoriesWithoutIgnorePatterns(files)

        allFiles.forEach { file ->
            if (processed.add(file.path) && isValidFile(file)) {
                addFileContext(file, project, contexts)
            }
        }

        return contexts
    }

    private fun collectAll(project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val processed = mutableSetOf<String>()

        project.basePath?.let { basePath ->
            val projectRoot = VfsUtil.findFileByIoFile(java.io.File(basePath), true)
            projectRoot?.let { root ->
                processDirectoryWithIgnorePatterns(root, project, contexts, processed)
            }
        }

        return contexts
    }

    private fun expandDirectoriesWithIgnorePatterns(
        files: List<VirtualFile>,
        project: Project
    ): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val seen = mutableSetOf<String>()

        files.forEach { file ->
            if (file.isDirectory) {
                collectFromDirectoryWithIgnorePatterns(file, project).forEach { dirFile ->
                    if (seen.add(dirFile.path)) {
                        result.add(dirFile)
                    }
                }
            } else {
                if (seen.add(file.path)) {
                    result.add(file)
                }
            }
        }

        return result
    }

    private fun expandDirectoriesWithoutIgnorePatterns(
        files: List<VirtualFile>,
    ): List<VirtualFile> {
        val result = mutableListOf<VirtualFile>()
        val seen = mutableSetOf<String>()

        files.forEach { file ->
            if (file.isDirectory) {
                collectFromDirectoryWithoutIgnorePatterns(file).forEach { dirFile ->
                    if (seen.add(dirFile.path)) {
                        result.add(dirFile)
                    }
                }
            } else {
                if (seen.add(file.path)) {
                    result.add(file)
                }
            }
        }

        return result
    }

    private fun collectFromDirectoryWithIgnorePatterns(
        directory: VirtualFile,
        project: Project
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
                    } else if (isValidFile(file) && !shouldIgnore(file, project)) {
                        files.add(file)
                    }
                }
            } catch (_: Exception) {
                // Skip inaccessible directories
            }
        }

        return files
    }

    private fun collectFromDirectoryWithoutIgnorePatterns(
        directory: VirtualFile,
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
                    } else if (isValidFile(file)) {
                        files.add(file)
                    }
                }
            } catch (_: Exception) {
                // Skip inaccessible directories
            }
        }

        return files
    }

    private fun processDirectoryWithIgnorePatterns(
        directory: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
        processed: MutableSet<String>
    ) {
        if (!directory.isValid || !directory.exists()) return

        val queue = ArrayDeque<VirtualFile>()
        queue.add(directory)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            try {
                current.children?.forEach { file ->
                    if (processed.contains(file.path)) return@forEach

                    if (file.isDirectory) {
                        queue.add(file)
                    } else if (isValidFile(file) && !shouldIgnore(file, project)) {
                        addFileContext(file, project, contexts)?.let {
                            processed.add(file.path)
                        }
                    }
                }
            } catch (_: Exception) {
                // Skip directories that can't be accessed
            }
        }
    }

    private fun processFileWithImports(
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
                    if (resolved.path !in processed && isJavaKotlinFile(resolved)) {
                        processFileWithImports(resolved, project, contexts, processed)
                    }
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

    private fun isJavaKotlin(file: VirtualFile, project: Project): Boolean =
        file.extension in setOf("java", "kt") &&
                file.isValid &&
                file.exists() &&
                isOfficialSourceFile(file, project)

    private fun isJavaKotlinFile(file: VirtualFile): Boolean =
        file.extension in setOf("java", "kt") &&
                file.isValid &&
                file.exists()

    private fun isTextFile(file: VirtualFile): Boolean {
        return try {
            val bytes = file.contentsToByteArray().take(1024).toByteArray()
            if (bytes.contains(0)) return false
            val text = String(bytes, Charsets.UTF_8)
            !text.contains('\uFFFD')
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidFile(file: VirtualFile): Boolean =
        file.isValid &&
                file.exists() &&
                !file.isDirectory &&
                isTextFile(file)

    private fun isSupported(file: VirtualFile, project: Project): Boolean =
        file.extension in setOf("java", "kt") &&
                file.isValid &&
                file.exists() &&
                isOfficialSourceFile(file, project)

    private fun isOfficialSourceFile(file: VirtualFile, project: Project): Boolean {
        val fileIndex = ProjectFileIndex.getInstance(project)

        if (!fileIndex.isInProject(file)) return false
        if (!fileIndex.isInSource(file)) return false
        if (fileIndex.isInGeneratedSources(file)) return false

        val module = fileIndex.getModuleForFile(file) ?: return false
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val sourceRoots = moduleRootManager.getSourceRoots(true)
        return sourceRoots.any { sourceRoot ->
            VfsUtil.isAncestor(sourceRoot, file, false)
        }
    }

    private fun getIgnoreNode(patterns: List<String>): IgnoreNode {
        val patternsHash = patterns.hashCode()
        if (ignoreNode == null || lastPatternsHash != patternsHash) {
            ignoreNode = IgnoreNode().apply {
                val patternsText = patterns.joinToString("\n")
                parse(ByteArrayInputStream(patternsText.toByteArray()))
            }
            lastPatternsHash = patternsHash
        }
        return ignoreNode!!
    }

    private fun shouldIgnore(file: VirtualFile, project: Project): Boolean {
        val settings = CodeCollectorSettings.getInstance(project)
        val enabledPatterns = settings.getEnabledPatterns()
        val relativePath = FileUtil.toSystemIndependentName(getRelativePath(file, project))

        if (enabledPatterns.isEmpty()) return false

        val ignoreNode = getIgnoreNode(enabledPatterns)
        return ignoreNode.isIgnored(relativePath, file.isDirectory) == MatchResult.IGNORED
    }

    private fun getRelativePath(file: VirtualFile, project: Project): String {
        val basePath = project.basePath ?: return file.name
        return file.path.removePrefix("$basePath/")
    }
}