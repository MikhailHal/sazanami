package io.github.mikhailhal.sazanami.collector

import io.github.mikhailhal.sazanami.common.CallableFqn
import io.github.mikhailhal.sazanami.common.ModuleName

/**
 * 変更された関数を表すドメインモデル
 *
 * git diffから検出された変更関数の情報を保持する。
 * マルチモジュール環境では同じFQNが複数モジュールに存在しうるため、
 * モジュール名も含めて一意に識別する。
 *
 * @property fqn 関数の完全修飾名
 * @property moduleName 関数が属するモジュール名
 * @property isTest 変更された関数自体が@Test付きで、そのまま実行対象になりうるか。
 *                  テスト関数には呼び出し元が存在しないためBFSでは辿り着けず、
 *                  リゾルバがこの関数自身を影響テストに含めるために使う (#44)
 */
data class ChangedFunction(
    val fqn: CallableFqn,
    val moduleName: ModuleName,
    val isTest: Boolean = false
)
