package io.github.mikhailhal.sonarkt.collector

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiDocumentManager
import io.github.mikhailhal.sonarkt.common.FunctionFqn
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

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
     * 変更された関数のFQNを収集
     *
     * @param diffOutput git diff --unified=0 の出力
     * @param ktFiles 解析対象のKtFileリスト
     * @return 変更された関数のFQN集合
     */
    fun collect(diffOutput: String, ktFiles: List<KtFile>): Set<FunctionFqn> {
        val fileDiffs = GitDiffParser.parseKotlinFiles(diffOutput)
        val changedFunctions = mutableSetOf<FunctionFqn>()

        if (fileDiffs.isEmpty()) return emptySet()

        for (ktFile in ktFiles) {
            // ファイルパスをリポジトリルートからの相対パスに変換して照合
            val filePath = ktFile.virtualFile?.path ?: continue
            val relativePath = extractRelativePath(filePath)

            val fileDiff = fileDiffs[relativePath] ?: continue

            // このファイルの変更された関数を収集
            val functions = collectChangedFunctionsInFile(ktFile, fileDiff)
            changedFunctions.addAll(functions)
        }

        return changedFunctions
    }

    /**
     * 単一ファイル内の変更された関数を収集
     */
    private fun collectChangedFunctionsInFile(
        ktFile: KtFile,
        fileDiff: FileDiff
    ): Set<FunctionFqn> {
        val changedFunctions = mutableSetOf<FunctionFqn>()
        val document = getDocument(ktFile) ?: return emptySet()

        ktFile.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                function.fqName?.asString()?.let { fqn ->
                    getFunctionLineRange(function, document)?.let { functionRange ->
                        val isChangedFunction = fileDiff.overlapsWithRange(functionRange)
                        if (isChangedFunction) {
                            changedFunctions.add(fqn)
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
     * ファイルパスからリポジトリルートからの相対パスを抽出
     *
     * TODO: 現在は簡易実装。実際にはプロジェクトルートを基準に計算すべき
     * 例: /home/user/project/src/main/Foo.kt → src/main/Foo.kt
     */
    private fun extractRelativePath(absolutePath: String): String {
        // 簡易実装: src/ または test/ から始まる部分を抽出
        val srcIndex = absolutePath.indexOf("/src/")
        if (srcIndex >= 0) {
            return absolutePath.substring(srcIndex + 1) // "/src/..." → "src/..."
        }

        // フォールバック: ファイル名のみ返す（完全一致は期待できない）
        return absolutePath.substringAfterLast("/")
    }
}
