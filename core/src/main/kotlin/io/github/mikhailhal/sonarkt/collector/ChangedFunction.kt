package io.github.mikhailhal.sonarkt.collector

import io.github.mikhailhal.sonarkt.common.FunctionFqn
import io.github.mikhailhal.sonarkt.common.ModuleName

/**
 * 変更された関数を表すドメインモデル
 *
 * git diffから検出された変更関数の情報を保持する。
 * マルチモジュール環境では同じFQNが複数モジュールに存在しうるため、
 * モジュール名も含めて一意に識別する。
 *
 * @property fqn 関数の完全修飾名
 * @property moduleName 関数が属するモジュール名
 */
data class ChangedFunction(
    val fqn: FunctionFqn,
    val moduleName: ModuleName
)
