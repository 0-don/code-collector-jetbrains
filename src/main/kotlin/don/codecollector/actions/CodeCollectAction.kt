package don.codecollector.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
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
            val totalLines = contexts.sumOf { it.content.lines().size }

            CopyPasteManager.getInstance().setContents(TextTransferable(output as CharSequence))
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("Code Collector")
                .createNotification(
                    "Copied context for ${contexts.size} files ($totalLines lines) from ${files.size} selected",
                    NotificationType.INFORMATION,
                ).notify(project)
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
        e.presentation.isEnabledAndVisible = files?.isNotEmpty() == true
    }
}
