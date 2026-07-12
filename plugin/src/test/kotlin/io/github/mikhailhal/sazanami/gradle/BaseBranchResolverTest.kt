package io.github.mikhailhal.sazanami.gradle

import kotlin.test.Test
import kotlin.test.assertEquals

class BaseBranchResolverTest {

    @Test
    fun `explicit branch takes priority over everything`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to "develop"
        )::get

        val result = BaseBranchResolver.resolve(
            explicitBranch = "origin/feature",
            envProvider = envProvider
        )

        assertEquals("origin/feature", result)
    }

    @Test
    fun `returns default when no explicit branch and no CI env`() {
        val result = BaseBranchResolver.resolve(
            explicitBranch = null,
            envProvider = { null }
        )

        assertEquals("origin/main", result)
    }

    @Test
    fun `blank explicit branch falls back to CI detection`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to "develop"
        )::get

        val result = BaseBranchResolver.resolve(
            explicitBranch = "  ",
            envProvider = envProvider
        )

        assertEquals("origin/develop", result)
    }

    // GitHub Actions
    @Test
    fun `detects GitHub Actions base branch`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to "main"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    @Test
    fun `detects GitHub Actions with develop branch`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to "develop"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/develop", result)
    }

    // GitLab CI
    @Test
    fun `detects GitLab CI merge request target branch`() {
        val envProvider = mapOf(
            "CI_MERGE_REQUEST_TARGET_BRANCH_NAME" to "main"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    // Bitbucket Pipelines
    @Test
    fun `detects Bitbucket Pipelines destination branch`() {
        val envProvider = mapOf(
            "BITBUCKET_PR_DESTINATION_BRANCH" to "master"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/master", result)
    }

    // Azure DevOps
    @Test
    fun `detects Azure DevOps target branch with refs prefix`() {
        val envProvider = mapOf(
            "SYSTEM_PULLREQUEST_TARGETBRANCH" to "refs/heads/main"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    @Test
    fun `detects Azure DevOps target branch without refs prefix`() {
        val envProvider = mapOf(
            "SYSTEM_PULLREQUEST_TARGETBRANCH" to "develop"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/develop", result)
    }

    // CircleCI
    @Test
    fun `detects CircleCI with pull request`() {
        val envProvider = mapOf(
            "CIRCLE_BRANCH" to "feature/test",
            "CIRCLE_PULL_REQUEST" to "https://github.com/org/repo/pull/123"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    @Test
    fun `CircleCI without pull request returns default`() {
        val envProvider = mapOf(
            "CIRCLE_BRANCH" to "main"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    // Priority tests
    @Test
    fun `GitHub Actions takes priority over GitLab CI`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to "github-branch",
            "CI_MERGE_REQUEST_TARGET_BRANCH_NAME" to "gitlab-branch"
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/github-branch", result)
    }

    // Edge cases
    @Test
    fun `empty CI env var is ignored`() {
        val envProvider = mapOf(
            "GITHUB_BASE_REF" to ""
        )::get

        val result = BaseBranchResolver.resolve(envProvider = envProvider)

        assertEquals("origin/main", result)
    }

    @Test
    fun `detectFromCI returns null when no CI env`() {
        val result = BaseBranchResolver.detectFromCI { null }

        assertEquals(null, result)
    }
}
