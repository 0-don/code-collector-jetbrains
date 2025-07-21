package don.codecollector.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.TextTransferable
import don.codecollector.ContextCollector

class CodeCollectAllAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            val collector = ContextCollector()
            val contexts = collector.collectAllFiles(project)
            val output = collector.formatContexts(contexts)

            CopyPasteManager.getInstance().setContents(TextTransferable(output as CharSequence))
            Messages.showInfoMessage(
                "Copied all code context for ${contexts.size} files",
                "Code Collector",
            )
        } catch (e: Exception) {
            Messages.showMessageDialog(
                project,
                "Error collecting code context: ${e.message}",
                "Code Collector Error",
                Messages.getErrorIcon(),
            )
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
