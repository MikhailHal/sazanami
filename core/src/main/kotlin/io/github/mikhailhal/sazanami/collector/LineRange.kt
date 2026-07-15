package io.github.mikhailhal.sazanami.collector

/**
 * 行範囲を表す値クラス
 *
 * IntRange をそのまま使うと意味が分かりにくいため、専用の型を用意。
 * 行番号は 1-indexed（エディタの表示と一致）
 */
data class LineRange(
    val startLine: Int,
    val endLine: Int
) {
    init {
        require(startLine >= 1) { "startLine must be >= 1, but was $startLine" }
        require(endLine >= startLine) { "endLine must be >= startLine, but was $endLine < $startLine" }
    }

    /**
     * 指定した行がこの範囲に含まれるかどうか
     */
    fun contains(line: Int): Boolean = line in startLine..endLine

    /**
     * 指定した行範囲と重なりがあるかどうか
     */
    fun overlaps(other: LineRange): Boolean {
        return startLine <= other.endLine && other.startLine <= endLine
    }

    /**
     * 指定した行のいずれかがこの範囲に含まれるかどうか
     */
    fun containsAny(lines: Set<Int>): Boolean = lines.any { contains(it) }

    override fun toString(): String = "L$startLine-$endLine"
}
