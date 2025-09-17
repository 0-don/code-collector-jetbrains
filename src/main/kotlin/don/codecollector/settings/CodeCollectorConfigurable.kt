package don.codecollector.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

class CodeCollectorConfigurable(
    project: Project,
) : Configurable {
    private val settings = CodeCollectorSettings.getInstance(project)
    private lateinit var checkboxTree: CheckboxTree
    private lateinit var rootNode: CheckedTreeNode
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

        // Create root node
        rootNode = CheckedTreeNode("Ignore Patterns")

        // Create checkbox tree
        checkboxTree =
            CheckboxTree(
                object : CheckboxTree.CheckboxTreeCellRenderer() {
                    override fun customizeRenderer(
                        tree: JTree,
                        value: Any,
                        selected: Boolean,
                        expanded: Boolean,
                        leaf: Boolean,
                        row: Int,
                        hasFocus: Boolean,
                    ) {
                        if (value is CheckedTreeNode && value.userObject is IgnorePattern) {
                            val pattern = value.userObject as IgnorePattern
                            textRenderer.append(pattern.pattern)
                        } else {
                            textRenderer.append(value.toString())
                        }
                    }
                },
                rootNode,
                CheckboxTreeBase.CheckPolicy(true, true, false, false), // Add this line
            )

        // Configure tree
        checkboxTree.isRootVisible = false
        checkboxTree.showsRootHandles = true
        TreeUtil.expandAll(checkboxTree)

        val decorator =
            ToolbarDecorator
                .createDecorator(checkboxTree)
                .setAddAction { addPattern() }
                .setRemoveAction { removePattern() }
                .setEditAction { editPattern() }
                .createPanel()

        mainPanel.add(JLabel("Ignore Patterns (check to enable):"), BorderLayout.NORTH)
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
            val ignorePattern = IgnorePattern(pattern.trim(), true)
            val node = CheckedTreeNode(ignorePattern)
            node.isChecked = true
            rootNode.add(node)
            (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
            TreeUtil.expandAll(checkboxTree)
        }
    }

    private fun removePattern() {
        val selectedPath = checkboxTree.selectionPath
        if (selectedPath != null) {
            val selectedNode = selectedPath.lastPathComponent as? CheckedTreeNode
            if (selectedNode != null && selectedNode.userObject is IgnorePattern) {
                rootNode.remove(selectedNode)
                (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
            }
        }
    }

    private fun editPattern() {
        val selectedPath = checkboxTree.selectionPath
        if (selectedPath != null) {
            val selectedNode = selectedPath.lastPathComponent as? CheckedTreeNode
            if (selectedNode != null && selectedNode.userObject is IgnorePattern) {
                val currentPattern = selectedNode.userObject as IgnorePattern
                val newPatternText =
                    JOptionPane.showInputDialog(
                        panel,
                        "Edit pattern:",
                        "Edit Pattern",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        currentPattern.pattern,
                    )
                if (newPatternText != null && newPatternText.toString().isNotBlank()) {
                    currentPattern.pattern = newPatternText.toString().trim()
                    (checkboxTree.model as DefaultTreeModel).nodeChanged(selectedNode)
                }
            }
        }
    }

    private fun resetToDefaults() {
        rootNode.removeAllChildren()
        CodeCollectorSettings.getDefaultIgnorePatterns().forEach { ignorePattern ->
            val node = CheckedTreeNode(ignorePattern)
            node.isChecked = ignorePattern.enabled
            rootNode.add(node)
        }
        (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
        TreeUtil.expandAll(checkboxTree)
    }

    override fun isModified(): Boolean {
        val currentPatterns = getCurrentPatterns()
        return currentPatterns != settings.state.ignorePatterns
    }

    private fun getCurrentPatterns(): List<IgnorePattern> {
        val patterns = mutableListOf<IgnorePattern>()
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChildAt(i) as CheckedTreeNode
            if (child.userObject is IgnorePattern) {
                val pattern = child.userObject as IgnorePattern
                patterns.add(IgnorePattern(pattern.pattern, child.isChecked))
            }
        }
        return patterns
    }

    override fun apply() {
        settings.state.ignorePatterns.clear()
        settings.state.ignorePatterns.addAll(getCurrentPatterns())
    }

    override fun reset() {
        rootNode.removeAllChildren()
        settings.state.ignorePatterns.forEach { ignorePattern ->
            val node = CheckedTreeNode(ignorePattern.copy()) // Copy to avoid reference issues
            node.isChecked = ignorePattern.enabled
            rootNode.add(node)
        }
        (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
        TreeUtil.expandAll(checkboxTree)
    }
}
