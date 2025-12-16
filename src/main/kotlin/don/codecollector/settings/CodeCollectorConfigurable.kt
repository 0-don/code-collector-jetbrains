package don.codecollector.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.ToolbarDecorator
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

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
        checkboxTree = CheckboxTree(
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
            CheckboxTreeBase.CheckPolicy(true, true, false, false),
        )

        // Configure tree
        checkboxTree.isRootVisible = false
        checkboxTree.showsRootHandles = true
        TreeUtil.expandAll(checkboxTree)

        // Enable drag and drop
        setupDragAndDrop()

        // Add double-click listener for editing
        setupDoubleClickEdit()

        val decorator = ToolbarDecorator.createDecorator(checkboxTree).setAddAction { addPattern() }
            .setRemoveAction { removePattern() }.setEditAction { editPattern() }.createPanel()

        mainPanel.add(
            JLabel("Ignore Patterns (check to enable, drag to reorder, double-click to edit):"),
            BorderLayout.NORTH
        )
        mainPanel.add(decorator, BorderLayout.CENTER)

        val resetButton = JButton("Reset to Defaults")
        resetButton.addActionListener { resetToDefaults() }
        mainPanel.add(resetButton, BorderLayout.SOUTH)

        return mainPanel
    }

    private fun setupDragAndDrop() {
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            checkboxTree.dragEnabled = true
            checkboxTree.dropMode = DropMode.INSERT
            checkboxTree.transferHandler = PatternTransferHandler()
        }
    }

    private fun setupDoubleClickEdit() {
        checkboxTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = checkboxTree.getPathForLocation(e.x, e.y)
                    if (path != null) {
                        val node = path.lastPathComponent as? CheckedTreeNode
                        if (node?.userObject is IgnorePattern) {
                            editPattern()
                        }
                    }
                }
            }
        })
    }

    private inner class PatternTransferHandler : TransferHandler() {

        override fun getSourceActions(c: JComponent): Int {
            return MOVE
        }

        override fun createTransferable(c: JComponent): Transferable? {
            val tree = c as JTree
            val path = tree.selectionPath ?: return null
            val node = path.lastPathComponent as? CheckedTreeNode ?: return null

            if (node.userObject !is IgnorePattern) return null

            // Store the index of the dragged item
            val sourceIndex = rootNode.getIndex(node)
            return PatternTransferable(sourceIndex)
        }

        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDataFlavorSupported(PatternTransferable.PATTERN_FLAVOR)) {
                return false
            }

            val dl = support.dropLocation as? JTree.DropLocation ?: return false
            return dl.path != null
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false

            val dl = support.dropLocation as JTree.DropLocation
            val dropIndex = dl.childIndex

            try {
                val transferable = support.transferable
                val sourceIndex = transferable.getTransferData(PatternTransferable.PATTERN_FLAVOR) as Int

                // Get target index
                val targetIndex = if (dropIndex == -1) rootNode.childCount else dropIndex

                // Don't move if dropping in the same position
                if (sourceIndex == targetIndex || sourceIndex + 1 == targetIndex) {
                    return false
                }

                // Get all current patterns
                val patterns = getCurrentPatterns().toMutableList()

                // Move the item
                val movedPattern = patterns.removeAt(sourceIndex)
                val finalTargetIndex = if (sourceIndex < targetIndex) targetIndex - 1 else targetIndex
                patterns.add(finalTargetIndex.coerceIn(0, patterns.size), movedPattern)

                // Rebuild the tree
                rebuildTree(patterns)

                // Select the moved item
                val newIndex = finalTargetIndex.coerceIn(0, rootNode.childCount - 1)
                if (newIndex < rootNode.childCount) {
                    val movedNode = rootNode.getChildAt(newIndex) as CheckedTreeNode
                    val newPath = TreePath(arrayOf(rootNode, movedNode))
                    checkboxTree.selectionPath = newPath
                    checkboxTree.scrollPathToVisible(newPath)
                }

                return true

            } catch (e: Exception) {
                return false
            }
        }

        override fun exportDone(source: JComponent, data: Transferable?, action: Int) {
            // No cleanup needed
        }
    }

    private class PatternTransferable(private val index: Int) : Transferable {

        companion object {
            val PATTERN_FLAVOR = DataFlavor(Int::class.java, "Pattern Index")
        }

        override fun getTransferDataFlavors(): Array<DataFlavor> {
            return arrayOf(PATTERN_FLAVOR)
        }

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
            return flavor == PATTERN_FLAVOR
        }

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) {
                throw UnsupportedFlavorException(flavor)
            }
            return index
        }
    }

    private fun rebuildTree(patterns: List<IgnorePattern>) {
        rootNode.removeAllChildren()
        patterns.forEach { ignorePattern ->
            val node = CheckedTreeNode(ignorePattern)
            node.isChecked = ignorePattern.enabled
            rootNode.add(node)
        }
        (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
        TreeUtil.expandAll(checkboxTree)
    }

    private fun addPattern() {
        val pattern = JOptionPane.showInputDialog(
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
                val newPatternText = JOptionPane.showInputDialog(
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
            val node = CheckedTreeNode(ignorePattern.copy())
            node.isChecked = ignorePattern.enabled
            rootNode.add(node)
        }
        (checkboxTree.model as DefaultTreeModel).nodeStructureChanged(rootNode)
        TreeUtil.expandAll(checkboxTree)
    }
}