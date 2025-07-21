package don.codecollector.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

class CodeCollectorConfigurable(
    project: Project,
) : Configurable {
    private val settings = CodeCollectorSettings.getInstance(project)
    private val listModel = DefaultListModel<String>()
    private val patternsList = JBList(listModel)
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Code Collector"

    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = createSettingsPanel()
        }
        reset()
        return panel!!
    }

    private fun createSettingsPanel(): JPanel {
        val mainPanel = JPanel(BorderLayout())

        val decorator =
            ToolbarDecorator
                .createDecorator(patternsList)
                .setAddAction { addPattern() }
                .setRemoveAction { removePattern() }
                .setEditAction { editPattern() }
                .createPanel()

        mainPanel.add(JLabel("Ignore Patterns (glob format):"), BorderLayout.NORTH)
        mainPanel.add(decorator, BorderLayout.CENTER)

        val resetButton = JButton("Reset to Defaults")
        resetButton.addActionListener { resetToDefaults() }
        mainPanel.add(resetButton, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun addPattern() {
        val pattern =
            JOptionPane.showInputDialog(
                panel,
                "Enter ignore pattern:",
                "Add Pattern",
                JOptionPane.PLAIN_MESSAGE,
            )
        if (pattern != null && pattern.isNotBlank()) {
            listModel.addElement(pattern.trim())
        }
    }

    private fun removePattern() {
        val selectedIndex = patternsList.selectedIndex
        if (selectedIndex >= 0) {
            listModel.removeElementAt(selectedIndex)
        }
    }

    private fun editPattern() {
        val selectedIndex = patternsList.selectedIndex
        if (selectedIndex >= 0) {
            val currentPattern = listModel.getElementAt(selectedIndex)
            val newPattern =
                JOptionPane.showInputDialog(
                    panel,
                    "Edit pattern:",
                    "Edit Pattern",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    currentPattern,
                )
            if (newPattern != null && newPattern.toString().isNotBlank()) {
                listModel.setElementAt(newPattern.toString().trim(), selectedIndex)
            }
        }
    }

    private fun resetToDefaults() {
        listModel.clear()
        CodeCollectorSettings.getDefaultIgnorePatterns().forEach { listModel.addElement(it) }
    }

    override fun isModified(): Boolean {
        val currentPatterns = (0 until listModel.size()).map { listModel.getElementAt(it) }
        return currentPatterns != settings.state.ignorePatterns
    }

    override fun apply() {
        settings.state.ignorePatterns.clear()
        (0 until listModel.size()).forEach {
            settings.state.ignorePatterns.add(listModel.getElementAt(it))
        }
    }

    override fun reset() {
        listModel.clear()
        settings.state.ignorePatterns.forEach { listModel.addElement(it) }
    }
}
