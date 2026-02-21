package io.github.mikhailhal.sonarkt.collector

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import io.github.mikhailhal.sonarkt.common.ModuleName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.nio.file.Path
import java.nio.file.Paths

/**
 * git diffから変更された関数を特定するCollector
 *
 * 処理フロー:
 * 1. git diff出力をパースして変更行範囲を取得 (GitDiffParser)
 * 2. 各KtFileを走査して関数の行範囲を取得
 * 3. 変更行範囲と関数の行範囲が重なる関数を「変更された関数」として収集
 *
 * 注意: 行番号は1-indexed（エディタの表示と一致）
 */
class ChangedFunctionCollector {

    /**
     * 変更された関数を収集
     *
     * @param diffOutput git diff --unified=0 の出力
     * @param ktFiles 解析対象のKtFileリスト
     * @param modulePathMapping モジュール名 → モジュールルートパスのマッピング
     *                         例: mapOf(":core" to "core", ":plugin" to "plugin")
     * @param projectRoot プロジェクトルートの絶対パス（相対パス計算に使用）
     * @return 変更された関数の集合
     */
    fun collect(
        diffOutput: String,
        ktFiles: List<KtFile>,
        modulePathMapping: Map<ModuleName, String>,
        projectRoot: Path
    ): Set<ChangedFunction> {
        val fileDiffs = GitDiffParser.parseKotlinFiles(diffOutput)
        val changedFunctions = mutableSetOf<ChangedFunction>()

        if (fileDiffs.isEmpty()) return emptySet()

        for (ktFile in ktFiles) {
            // ファイルパスをプロジェクトルートからの相対パスに変換して照合
            val filePath = ktFile.virtualFile?.path ?: continue
            val relativePath = extractRelativePath(filePath, projectRoot)

            val fileDiff = fileDiffs[relativePath] ?: continue

            // ファイルパスからモジュール名を解決
            val moduleName = resolveModuleName(relativePath, modulePathMapping)

            // このファイルの変更された関数を収集
            val functions = collectChangedFunctionsInFile(ktFile, fileDiff, moduleName)
            changedFunctions.addAll(functions)
        }

        return changedFunctions
    }

    /**
     * ファイルパスからモジュール名を解決
     *
     * 最長プレフィックスマッチングでモジュールを特定する。
     * 例: "core/src/main/kotlin/Foo.kt" → ":core"
     *
     * @param filePath リポジトリルートからの相対パス
     * @param modulePathMapping モジュール名 → モジュールルートパスのマッピング
     * @return 該当するモジュール名、見つからない場合は空文字列
     */
    private fun resolveModuleName(
        filePath: String,
        modulePathMapping: Map<ModuleName, String>
    ): ModuleName {
        var bestMatch: ModuleName = ""
        var bestMatchLength = 0

        for ((moduleName, modulePath) in modulePathMapping) {
            if (filePath.startsWith(modulePath) && modulePath.length > bestMatchLength) {
                bestMatch = moduleName
                bestMatchLength = modulePath.length
            }
        }

        return bestMatch
    }

    /**
     * 単一ファイル内の変更された関数を収集
     */
    private fun collectChangedFunctionsInFile(
        ktFile: KtFile,
        fileDiff: FileDiff,
        moduleName: ModuleName
    ): Set<ChangedFunction> {
        val changedFunctions = mutableSetOf<ChangedFunction>()
        val document = getDocument(ktFile) ?: return emptySet()

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                function.fqName?.asString()?.let { fqn ->
                    getFunctionLineRange(function, document)?.let { functionRange ->
                        val isChangedFunction = fileDiff.overlapsWithRange(functionRange)
                        if (isChangedFunction) {
                            changedFunctions.add(ChangedFunction(fqn, moduleName))
                        }
                    }
                }
                super.visitNamedFunction(function)
            }
        })

        return changedFunctions
    }

    /**
     * 関数の行範囲を取得
     *
     * @return 関数の開始行から終了行までのLineRange（1-indexed）
     */
    private fun getFunctionLineRange(function: KtNamedFunction, document: Document): LineRange? {
        val textRange = function.textRange ?: return null

        // Document.getLineNumber() は0-indexedなので +1 して1-indexedに変換
        val startLine = document.getLineNumber(textRange.startOffset) + 1
        val endLine = document.getLineNumber(textRange.endOffset) + 1

        return LineRange(startLine, endLine)
    }

    /**
     * KtFileからDocumentを取得
     */
    private fun getDocument(ktFile: KtFile): Document? {
        val project = ktFile.project
        return PsiDocumentManager.getInstance(project).getDocument(ktFile)
    }

    /**
     * ファイルパスからプロジェクトルートからの相対パスを計算
     *
     * @param absolutePath ファイルの絶対パス
     * @param projectRoot プロジェクトルートの絶対パス
     * @return プロジェクトルートからの相対パス
     */
    private fun extractRelativePath(absolutePath: String, projectRoot: Path): String {
        return try {
            val absPath = Paths.get(absolutePath)
            projectRoot.relativize(absPath).toString()
        } catch (e: IllegalArgumentException) {
            // 異なるルートの場合（Windowsのドライブが違うなど）
            // フォールバック: ファイル名のみ返す
            absolutePath.substringAfterLast("/")
        }
    }
}
