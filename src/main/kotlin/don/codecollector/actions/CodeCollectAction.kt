package don.codecollector.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.TextTransferable
import don.codecollector.ContextCollector

class CodeCollectAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return

        try {
            val collector = ContextCollector()
            val contexts = collector.collectFromFiles(files.toList(), project)
            val output = collector.formatContexts(contexts)

            CopyPasteManager.getInstance().setContents(TextTransferable(output as CharSequence))
            Messages.showInfoMessage(
                "Copied context for ${contexts.size} files (from ${files.size} selected)",
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
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        e.presentation.isEnabledAndVisible = files?.any { isSupported(it) } == true
    }

    private fun isSupported(file: VirtualFile): Boolean = file.extension in setOf("java", "kt")
}
