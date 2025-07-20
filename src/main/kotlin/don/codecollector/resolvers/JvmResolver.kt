package don.codecollector.resolvers

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class JvmResolver {
    fun resolve(
        importPath: String,
        contextFile: VirtualFile,
        project: Project,
    ): VirtualFile? {
        // Try to find the class using PSI
        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(importPath, GlobalSearchScope.projectScope(project))

        return psiClass?.containingFile?.virtualFile
    }
}
