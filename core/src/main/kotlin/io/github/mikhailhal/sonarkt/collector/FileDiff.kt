package io.github.mikhailhal.sonarkt.collector

/**
 * 1ファイル分のdiff情報を表すデータクラス
 *
 * git diff --unified=0 の出力をパースした結果を保持。
 * 変更された行の範囲（追加・変更のみ、削除は含まない）を保持する。
 */
data class FileDiff(
    /** 変更されたファイルのパス（リポジトリルートからの相対パス） */
    val filePath: String,

    /** 変更された行の範囲リスト（追加・変更行のみ） */
    val changedLineRanges: List<LineRange>
) {
    /**
     * 指定した行がこのファイルの変更範囲に含まれるかどうか
     */
    fun containsLine(line: Int): Boolean =
        changedLineRanges.any { it.contains(line) }

    /**
     * 指定した行範囲と重なりがあるかどうか
     */
    fun overlapsWithRange(range: LineRange): Boolean =
        changedLineRanges.any { it.overlaps(range) }

    /**
     * Kotlinファイルかどうか
     */
    fun isKotlinFile(): Boolean =
        filePath.endsWith(".kt") || filePath.endsWith(".kts")
}
