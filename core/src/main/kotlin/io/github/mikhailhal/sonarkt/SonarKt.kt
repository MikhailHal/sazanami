package io.github.mikhailhal.sonarkt

import com.intellij.openapi.util.Disposer
import io.github.mikhailhal.sonarkt.collector.ChangedFunctionCollector
import io.github.mikhailhal.sonarkt.emitter.AffectedTestEmitter
import io.github.mikhailhal.sonarkt.processor.AffectedTestResolver
import io.github.mikhailhal.sonarkt.processor.GraphBuilder
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

/**
 * sonar-kt のメインAPI
 *
 * 使い方:
 * ```
 * val affected = SonarKt.findAffectedTests(
 *     diff = "git diff output...",
 *     sourceRoots = listOf(Path.of("src/main/kotlin"), Path.of("src/test/kotlin"))
 * )
 * ```
 */
object SonarKt {

    /**
     * 変更されたコードに影響を受けるテストのFQN一覧を返す
     *
     * @param diff git diff --unified=0 の出力
     * @param sourceRoots Kotlinソースのルートディレクトリ
     * @return 影響テストのFQN集合
     */
    fun findAffectedTests(diff: String, sourceRoots: List<Path>): Set<String> {
        if (diff.isEmpty() || sourceRoots.isEmpty()) {
            return emptySet()
        }

        val projectDisposable = Disposer.newDisposable("sonar-kt")

        try {
            val ktFiles = extractKtFilesViaSession(sourceRoots, projectDisposable)
            if (ktFiles.isEmpty()) {
                return emptySet()
            }

            val changedFunctions = ChangedFunctionCollector().collect(diff, ktFiles)
            val graph = GraphBuilder().build(ktFiles)
            return AffectedTestResolver(graph).findAffectedTests(changedFunctions)
        } finally {
            Disposer.dispose(projectDisposable)
        }
    }

    /**
     * 影響テストを改行区切りの文字列で返す
     */
    fun findAffectedTestsAsString(diff: String, sourceRoots: List<Path>): String {
        val affected = findAffectedTests(diff, sourceRoots)
        return AffectedTestEmitter.emit(affected)
    }

    private fun extractKtFilesViaSession(sourceRoots: List<Path>, disposable: com.intellij.openapi.Disposable): List<KtFile> {
        val session = buildStandaloneAnalysisAPISession(disposable) {
            buildKtModuleProvider {
                platform = JvmPlatforms.defaultJvmPlatform

                addModule(buildKtSourceModule {
                    moduleName = "project"
                    platform = JvmPlatforms.defaultJvmPlatform
                    sourceRoots.forEach { addSourceRoot(it) }
                })
            }
        }

        // モジュールに紐づいたファイル一覧からKtFileのみ抽出して返却
        return session.modulesWithFiles
            .flatMap { it.value }
            .filterIsInstance<KtFile>()
    }
}
