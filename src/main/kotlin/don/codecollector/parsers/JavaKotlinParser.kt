package don.codecollector.parsers

import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiTypeElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtVisitorVoid

data class ImportInfo(
    val module: String,
    val line: Int,
)

class JavaKotlinParser {
    fun parseImports(psiFile: PsiFile): List<ImportInfo> =
        when (psiFile) {
            is PsiJavaFile -> parseJavaImports(psiFile) + parseJavaReferences(psiFile)
            is KtFile -> parseKotlinImports(psiFile) + parseKotlinReferences(psiFile)
            else -> emptyList()
        }

    private fun parseJavaImports(javaFile: PsiJavaFile): List<ImportInfo> =
        javaFile.importList?.importStatements?.mapNotNull { importStatement ->
            importStatement.qualifiedName?.let { qualifiedName ->
                val line = getLineNumber(importStatement)
                ImportInfo(qualifiedName, line)
            }
        } ?: emptyList()

    private fun parseKotlinImports(kotlinFile: KtFile): List<ImportInfo> =
        kotlinFile.importDirectives.mapNotNull { import ->
            import.importedFqName?.asString()?.let { fqName ->
                val line = getLineNumber(import)
                ImportInfo(fqName, line)
            }
        }

    private fun parseJavaReferences(javaFile: PsiJavaFile): List<ImportInfo> {
        val references = mutableSetOf<ImportInfo>()
        val packageName = javaFile.packageName

        javaFile.accept(
            object : JavaRecursiveElementVisitor() {
                override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                    super.visitReferenceElement(reference)
                    processJavaReference(reference, packageName, references)
                }

                override fun visitTypeElement(type: PsiTypeElement) {
                    super.visitTypeElement(type)
                    // Handle type references in field declarations, method parameters, etc.
                    val typeReference = type.innermostComponentReferenceElement
                    typeReference?.let { ref ->
                        processJavaReference(ref, packageName, references)
                    }
                }
            },
        )

        return references.toList()
    }

    private fun parseKotlinReferences(kotlinFile: KtFile): List<ImportInfo> {
        val references = mutableSetOf<ImportInfo>()
        val packageName = kotlinFile.packageFqName.asString()

        kotlinFile.accept(
            object : KtVisitorVoid() {
                override fun visitUserType(type: KtUserType) {
                    super.visitUserType(type)
                    processKotlinTypeReference(type, packageName, references)
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    super.visitCallExpression(expression)
                    // Handle constructor calls and method calls
                    val callee = expression.calleeExpression
                    if (callee is KtSimpleNameExpression) {
                        processKotlinNameReference(callee, packageName, references)
                    }
                }

                override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                    super.visitDotQualifiedExpression(expression)
                    // Handle qualified expressions like ClassName.method()
                    val receiver = expression.receiverExpression
                    if (receiver is KtSimpleNameExpression) {
                        processKotlinNameReference(receiver, packageName, references)
                    }
                }
            },
        )

        return references.toList()
    }

    private fun processJavaReference(
        reference: PsiJavaCodeReferenceElement,
        currentPackage: String,
        references: MutableSet<ImportInfo>,
    ) {
        val referenceName = reference.referenceName ?: return
        if (!isValidClassName(referenceName)) return

        when (val resolved = reference.resolve()) {
            is PsiClass -> {
                val qualifiedName = resolved.qualifiedName
                if (qualifiedName != null &&
                    shouldIncludeReference(
                        qualifiedName,
                        currentPackage,
                    )
                ) {
                    val line = getLineNumber(reference)
                    references.add(ImportInfo(qualifiedName, line))
                }
            }
        }
    }

    private fun processKotlinTypeReference(
        type: KtUserType,
        currentPackage: String,
        references: MutableSet<ImportInfo>,
    ) {
        val referenceName = type.referencedName ?: return
        if (!isValidClassName(referenceName)) return

        // Try to resolve the reference
        val reference = type.referenceExpression?.mainReference
        when (val resolved = reference?.resolve()) {
            is KtClass -> {
                val fqName = resolved.fqName?.asString()
                if (fqName != null && shouldIncludeReference(fqName, currentPackage)) {
                    val line = getLineNumber(type)
                    references.add(ImportInfo(fqName, line))
                }
            }

            is PsiClass -> {
                val qualifiedName = resolved.qualifiedName
                if (qualifiedName != null &&
                    shouldIncludeReference(
                        qualifiedName,
                        currentPackage,
                    )
                ) {
                    val line = getLineNumber(type)
                    references.add(ImportInfo(qualifiedName, line))
                }
            }

            null -> {
                // If we can't resolve, assume it's in the same package
                if (currentPackage.isNotEmpty()) {
                    val assumedFqName = "$currentPackage.$referenceName"
                    val line = getLineNumber(type)
                    references.add(ImportInfo(assumedFqName, line))
                }
            }
        }
    }

    private fun processKotlinNameReference(
        nameExpr: KtSimpleNameExpression,
        currentPackage: String,
        references: MutableSet<ImportInfo>,
    ) {
        val referenceName = nameExpr.getReferencedName()
        if (!isValidClassName(referenceName)) return

        when (val resolved = nameExpr.mainReference.resolve()) {
            is KtClass -> {
                val fqName = resolved.fqName?.asString()
                if (fqName != null && shouldIncludeReference(fqName, currentPackage)) {
                    val line = getLineNumber(nameExpr)
                    references.add(ImportInfo(fqName, line))
                }
            }

            is PsiClass -> {
                val qualifiedName = resolved.qualifiedName
                if (qualifiedName != null &&
                    shouldIncludeReference(
                        qualifiedName,
                        currentPackage,
                    )
                ) {
                    val line = getLineNumber(nameExpr)
                    references.add(ImportInfo(qualifiedName, line))
                }
            }
        }
    }

    private fun shouldIncludeReference(
        fqName: String,
        currentPackage: String,
    ): Boolean {
        // Include if it's in the same package (same-level references)
        if (fqName.startsWith("$currentPackage.") &&
            !fqName.substring(currentPackage.length + 1).contains('.')
        ) {
            return true
        }

        // Include if it's in a different package within the same project
        // (exclude standard library classes)
        return !isStandardLibraryClass(fqName)
    }

    private fun isStandardLibraryClass(fqName: String): Boolean =
        fqName.startsWith("java.") ||
            fqName.startsWith("javax.") ||
            fqName.startsWith("kotlin.") ||
            fqName.startsWith("kotlinx.") ||
            fqName.startsWith("android.") ||
            fqName.startsWith("androidx.")

    private fun isValidClassName(name: String): Boolean =
        name.isNotEmpty() &&
            name[0].isUpperCase() &&
            !isPrimitiveType(name)

    private fun isPrimitiveType(name: String): Boolean =
        name in
            setOf(
                "String",
                "Object",
                "List",
                "Map",
                "Set",
                "Collection",
                "Integer",
                "Long",
                "Double",
                "Float",
                "Boolean",
                "Character",
                "Byte",
                "Short",
                "ArrayList",
                "HashMap",
                "HashSet",
                "Optional",
            )

    private fun getLineNumber(element: PsiElement): Int {
        val document =
            PsiDocumentManager
                .getInstance(element.project)
                .getDocument(element.containingFile)
        return document?.getLineNumber(element.textOffset)?.plus(1) ?: 0
    }
}
