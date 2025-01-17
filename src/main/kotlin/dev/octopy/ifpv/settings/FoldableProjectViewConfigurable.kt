package dev.octopy.ifpv.settings

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.not
import com.intellij.util.ui.tree.TreeUtil
import dev.octopy.ifpv.ifpvBundle.message
import dev.octopy.ifpv.bindSelected
import dev.octopy.ifpv.createPredicate
import dev.octopy.ifpv.projectView.FoldableTreeStructureProvider
import java.awt.Dimension
import javax.swing.BorderFactory.createEmptyBorder

class ifpvConfigurable(project: Project) : BoundSearchableConfigurable(
    helpTopic = "ifpv",
    _id = "ifpv",
    displayName = "ifpv",
), NoScroll {

    companion object {
        const val ID = "dev.octopy.ifpv.options.ifpvConfigurable"
    }

    private val settings = project.service<FoldableProjectSettings>()

    private val propertyGraph = PropertyGraph()
    private val settingsProperty = propertyGraph.lazyProperty { FoldableProjectSettings().apply { copyFrom(settings) } }
    private val foldingEnabledPredicate = settingsProperty.createPredicate(FoldableProjectSettings::foldingEnabled)
    private val hideAllGroupsPredicate = settingsProperty.createPredicate(FoldableProjectSettings::hideAllGroups)

    private val ruleProperty = propertyGraph
        .lazyProperty<Rule?> { null }
        .apply {
            afterChange {
                ApplicationManager.getApplication().invokeLater {
                    rulesTable.tableView.updateUI()
                }

                settingsProperty.setValue(null, FoldableProjectState::rules, settingsProperty.get())
            }
        }

    private val rulesTable = FoldableRulesTable(settingsProperty)
    private val rulesEditor = FoldableRulesEditor(ruleProperty)

    private val settingsPanel = panel {
        row {
            checkBox(message("ifpv.settings.foldingEnabled"))
                .bindSelected(settingsProperty, FoldableProjectSettings::foldingEnabled)
                .comment(message("ifpv.settings.foldingEnabled.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                .applyToComponent { setMnemonic('e') }
        }

        rowsRange {
            row {
                checkBox(message("ifpv.settings.caseSensitive"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::caseSensitive)
                    .comment(message("ifpv.settings.caseSensitive.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('c') }
            }

            row {
                checkBox(message("ifpv.settings.matchDirectories"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::matchDirectories)
                    .comment(
                        message("ifpv.settings.matchDirectories.comment"),
                        MAX_LINE_LENGTH_WORD_WRAP
                    )
                    .applyToComponent { setMnemonic('d') }
            }

            row {
                checkBox(message("ifpv.settings.foldIgnoredFiles"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::foldIgnoredFiles)
                    .comment(
                        message("ifpv.settings.foldIgnoredFiles.comment"),
                        MAX_LINE_LENGTH_WORD_WRAP
                    )
                    .applyToComponent { setMnemonic('h') }

                visible(true)
            }

            row {
                checkBox(message("ifpv.settings.hideAllGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideAllGroups)
                    .comment(message("ifpv.settings.hideAllGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .gap(RightGap.SMALL)
                    .applyToComponent { setMnemonic('i') }

                ContextHelpLabel
                    .create(
                        message("ifpv.settings.hideAllGroups.help"),
                        message("ifpv.settings.hideAllGroups.help.description"),
                    )
                    .let(::cell)

                visible(true)
            }

            row {
                checkBox(message("ifpv.settings.hideEmptyGroups"))
                    .bindSelected(settingsProperty, FoldableProjectSettings::hideEmptyGroups)
                    .comment(message("ifpv.settings.hideEmptyGroups.comment"), MAX_LINE_LENGTH_WORD_WRAP)
                    .applyToComponent { setMnemonic('h') }
                    .enabledIf(hideAllGroupsPredicate.not())

                visible(true)
            }
        }.enabledIf(foldingEnabledPredicate)
    }

    private val projectView by lazy {
        object : ProjectViewPane(project) {

            private val treeStructureProvider =
                FoldableTreeStructureProvider(project)
                    .withProjectViewPane(this)
                    .withState(settingsProperty)

            override fun enableDnD() = Unit

            override fun createStructure() = object : AbstractProjectTreeStructure(project) {

                override fun getProviders() = listOf(treeStructureProvider)
            }
        }
    }

    private val splitter = OnePixelSplitter(false, .6f, .4f, .6f).apply {
        firstComponent = settingsPanel.apply {
            border = createEmptyBorder(10, 10, 10, 30)
        }
        secondComponent = projectView.createComponent().apply {
            border = createEmptyBorder()
            preferredSize = Dimension()
        }
        setHonorComponentsMinimumSize(false)
        TreeUtil.promiseExpand(projectView.tree, 2)
    }

    override fun createPanel() =
        panel {
            row {
                cell(splitter)
                    .align(Align.FILL)
            }
            group(message("ifpv.settings.foldingRules")) {
                row {
                    cell(rulesTable.component)
                        .align(Align.FILL)
                        .resizableColumn()

                    cell(rulesEditor.createPanel())
                        .applyIfEnabled()

                    with(rulesTable.tableView) {
                        selectionModel.addListSelectionListener {
                            ruleProperty.set(selectedObject)
                        }
                    }
                }
            }
        }

    override fun getId() = ID

    override fun getDisplayName() = message("ifpv.name")

    override fun isModified() = settingsProperty.get() != settings

    override fun apply() {
        settings.copyFrom(settingsProperty.get())
        invalidateTable()
    }

    override fun reset() {
        settingsProperty.set(settingsProperty.get().apply {
            copyFrom(settings)
        })
        invalidateTable()
    }

    private fun invalidateTable() = rulesTable.apply {
        setValues(settingsProperty.get().rules)
        tableView.selection.clear()
    }
}
