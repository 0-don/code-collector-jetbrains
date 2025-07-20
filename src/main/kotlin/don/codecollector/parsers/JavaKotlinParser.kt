package don.codecollector.parsers

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

data class ImportInfo(
    val module: String,
    val line: Int,
)

class JavaKotlinParser {
    fun parseImports(psiFile: PsiFile): List<ImportInfo> =
        when (psiFile) {
            is PsiJavaFile -> parseJavaImports(psiFile)
            is KtFile -> parseKotlinImports(psiFile)
            else -> emptyList()
        }

    private fun parseJavaImports(javaFile: PsiJavaFile): List<ImportInfo> =
        javaFile.importList?.importStatements?.mapNotNull { importStatement ->
            importStatement.qualifiedName?.let { qualifiedName ->
                val document =
                    PsiDocumentManager
                        .getInstance(javaFile.project)
                        .getDocument(javaFile)
                val line = document?.getLineNumber(importStatement.textOffset)?.plus(1) ?: 0
                ImportInfo(qualifiedName, line)
            }
        } ?: emptyList()

    private fun parseKotlinImports(kotlinFile: KtFile): List<ImportInfo> =
        PsiTreeUtil
            .findChildrenOfType(kotlinFile, KtImportDirective::class.java)
            .mapNotNull { import ->
                // Use importedFqName instead of deprecated APIs
                val fqName = import.importedFqName?.asString()
                fqName?.let {
                    val document =
                        PsiDocumentManager
                            .getInstance(kotlinFile.project)
                            .getDocument(kotlinFile)
                    val line = document?.getLineNumber(import.textOffset)?.plus(1) ?: 0
                    ImportInfo(it, line)
                }
            }
}
