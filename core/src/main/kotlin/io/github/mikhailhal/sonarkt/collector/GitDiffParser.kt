package io.github.mikhailhal.sonarkt.collector

/**
 * git diff --unified=0 の出力をパースする
 *
 * 出力フォーマット例:
 * ```
 * diff --git a/src/main/Foo.kt b/src/main/Foo.kt
 * --- a/src/main/Foo.kt
 * +++ b/src/main/Foo.kt
 * @@ -10,2 +10,3 @@ fun existing()
 * +    added line 1
 * +    added line 2
 * +    added line 3
 * @@ -20 +21 @@ class Bar
 * -    old line
 * +    new line
 * ```
 *
 * hunkヘッダー `@@ -old_start,old_count +new_start,new_count @@` の意味:
 * - `-old_start,old_count`: 旧ファイルの行番号と行数
 * - `+new_start,new_count`: 新ファイルの行番号と行数
 * - countが1の場合は省略されることがある（例: `@@ -10 +10 @@`）
 *
 * 変更検出に必要なのは新ファイル側の行範囲（+new_start,new_count）のみ。
 * 削除行（new_count=0）は関数特定には使わないのでスキップする。
 */
object GitDiffParser {

    private val FILE_HEADER_REGEX = Regex("""^diff --git a/.+ b/(.+)$""")
    private val HUNK_HEADER_REGEX = Regex("""^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@.*$""")

    /**
     * git diff出力をパースしてファイルごとのdiff情報を返す
     *
     * @param diffOutput git diff --unified=0 の出力
     * @return ファイルパスをキーとしたFileDiffのマップ
     */
    fun parse(diffOutput: String): Map<String, FileDiff> {
        val result = mutableMapOf<String, FileDiff>()
        var currentFilePath: String? = null
        val currentRanges = mutableListOf<LineRange>()

        for (line in diffOutput.lines()) {
            val fileMatch = FILE_HEADER_REGEX.find(line)
            if (fileMatch != null) {
                // 前のファイルの結果を保存
                currentFilePath?.let { path ->
                    if (currentRanges.isNotEmpty()) {
                        result[path] = FileDiff(path, currentRanges.toList())
                    }
                }
                // 新しいファイルの処理開始
                currentFilePath = fileMatch.groupValues[1]
                currentRanges.clear()
                continue
            }

            // hunkヘッダーをパース
            val hunkMatch = HUNK_HEADER_REGEX.find(line)
            if (hunkMatch != null && currentFilePath != null) {
                val newStart = hunkMatch.groupValues[1].toInt()
                val newCount = hunkMatch.groupValues[2].ifEmpty { "1" }.toInt()

                // 削除のみ（new_count=0）の場合はスキップ
                // 理由: 削除された行は新ファイルに存在しないため、関数特定に使えない
                if (newCount > 0) {
                    val range = LineRange(newStart, newStart + newCount - 1)
                    currentRanges.add(range)
                }
            }
        }

        // 最後のファイルの結果を保存
        currentFilePath?.let { path ->
            if (currentRanges.isNotEmpty()) {
                result[path] = FileDiff(path, currentRanges.toList())
            }
        }

        return result
    }

    /**
     * Kotlinファイルのみをフィルタリングして返す
     */
    fun parseKotlinFiles(diffOutput: String): Map<String, FileDiff> {
        return parse(diffOutput).filterValues { it.isKotlinFile() }
    }
}
