package don.codecollector.core

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import don.codecollector.parsers.JavaKotlinParser
import don.codecollector.resolvers.JvmResolver
import org.jetbrains.kotlin.idea.KotlinFileType

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

        files.filter { isSupported(it) }.forEach { file ->
            processFile(file, project, contexts, processed)
        }

        return contexts
    }

    fun collectAllFiles(project: Project): List<FileContext> {
        val contexts = mutableListOf<FileContext>()
        val scope = GlobalSearchScope.projectScope(project)

        // Find all Java and Kotlin files
        val javaFiles = FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)
        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)

        (javaFiles + kotlinFiles).forEach { file ->
            val relativePath = getRelativePath(file, project)
            val content = String(file.contentsToByteArray())
            contexts.add(FileContext(file.path, content, relativePath))
        }

        return contexts
    }

    private fun processFile(
        file: VirtualFile,
        project: Project,
        contexts: MutableList<FileContext>,
        processed: MutableSet<String>,
    ) {
        if (file.path in processed || !isSupported(file)) return

        processed.add(file.path)
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

    private fun isSupported(file: VirtualFile): Boolean = file.extension in setOf("java", "kt")

    private fun getRelativePath(
        file: VirtualFile,
        project: Project,
    ): String {
        val basePath = project.basePath ?: return file.name
        return file.path.removePrefix("$basePath/")
    }
}
