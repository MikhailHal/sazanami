package io.github.mikhailhal.sazanami.gradle

/**
 * ベースブランチを解決するシングルトン
 *
 * 優先順位:
 * 1. 明示的な設定値
 * 2. CI環境変数からの自動検出
 * 3. デフォルト値 (origin/main)
 */
object BaseBranchResolver {

    const val DEFAULT_BASE_BRANCH = "origin/main"

    /**
     * ベースブランチを解決する
     *
     * @param explicitBranch 明示的に設定されたブランチ（null可）
     * @param envProvider 環境変数プロバイダー（テスト用にDI可能）
     * @return 解決されたベースブランチ
     */
    fun resolve(
        explicitBranch: String? = null,
        envProvider: (String) -> String? = { System.getenv(it) }
    ): String {
        // 1. 明示的な設定がある場合
        if (!explicitBranch.isNullOrBlank()) {
            return explicitBranch
        }

        // 2. CI環境の自動検出
        detectFromCI(envProvider)?.let { return it }

        // 3. デフォルト
        return DEFAULT_BASE_BRANCH
    }

    /**
     * CI環境からベースブランチを自動検出
     */
    internal fun detectFromCI(envProvider: (String) -> String?): String? {
        // GitHub Actions
        envProvider("GITHUB_BASE_REF")?.takeIf { it.isNotEmpty() }?.let {
            return "origin/$it"
        }

        // GitLab CI
        envProvider("CI_MERGE_REQUEST_TARGET_BRANCH_NAME")?.takeIf { it.isNotEmpty() }?.let {
            return "origin/$it"
        }

        // Bitbucket Pipelines
        envProvider("BITBUCKET_PR_DESTINATION_BRANCH")?.takeIf { it.isNotEmpty() }?.let {
            return "origin/$it"
        }

        // Azure DevOps
        envProvider("SYSTEM_PULLREQUEST_TARGETBRANCH")?.takeIf { it.isNotEmpty() }?.let {
            // Azure returns refs/heads/main format
            val branch = it.removePrefix("refs/heads/")
            return "origin/$branch"
        }

        // CircleCI (PRの場合のみ)
        envProvider("CIRCLE_BRANCH")?.takeIf { it.isNotEmpty() }?.let { currentBranch ->
            // CircleCIはベースブランチを直接提供しないが、PRの場合はmainと比較することが多い
            // CIRCLE_PULL_REQUEST が設定されている場合のみデフォルトを返す
            if (envProvider("CIRCLE_PULL_REQUEST")?.isNotEmpty() == true) {
                return DEFAULT_BASE_BRANCH
            }
        }

        return null
    }
}
