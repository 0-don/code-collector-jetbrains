package don.codecollector.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class IgnorePattern(
    var pattern: String = "",
    var enabled: Boolean = true,
)

@Service(Service.Level.PROJECT)
@State(
    name = "CodeCollectorSettings",
    storages = [Storage("codecollector.xml")],
)
class CodeCollectorSettings : PersistentStateComponent<CodeCollectorSettings.State> {
    data class State(
        var ignorePatterns: MutableList<IgnorePattern> = getDefaultIgnorePatterns(),
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    // Helper method to get only enabled patterns
    fun getEnabledPatterns(): List<String> = myState.ignorePatterns.filter { it.enabled }.map { it.pattern }

    companion object {
        fun getInstance(project: Project): CodeCollectorSettings = project.service<CodeCollectorSettings>()

        fun getDefaultIgnorePatterns(): MutableList<IgnorePattern> =
            mutableListOf(
                // Java/Kotlin build outputs
                "target/**",
                "build/**",
                "out/**",
                "classes/**",
                "bin/**",
                // Gradle
                ".gradle/**",
                "gradlew",
                "gradlew.bat",
                "gradle/wrapper/**",
                // Maven
                ".mvn/**",
                "mvnw",
                "mvnw.cmd",
                // IDE files
                ".idea/**",
                ".run/**",
                "*.iml",
                "*.iws",
                "*.ipr",
                ".vscode/**",
                ".eclipse/**",
                ".metadata/**",
                ".classpath",
                ".project",
                ".settings/**",
                // Logs and temp files
                "*.log",
                "*.tmp",
                "*.swp",
                "*.bak",
                // Version control
                ".git/**",
                ".svn/**",
                // OS files
                ".DS_Store",
                "Thumbs.db",
                // JAR/WAR files
                "*.jar",
                "*.war",
                "*.ear",
                // Generated sources (common patterns)
                "**/generated/**",
                "**/generated-sources/**",
                "**/generated-test-sources/**",
                "**/wiremock/**",
                "**/docker/**",
            ).map { IgnorePattern(it, true) }.toMutableList()
    }
}
