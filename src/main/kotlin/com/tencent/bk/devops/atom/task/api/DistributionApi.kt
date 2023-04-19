package com.tencent.bk.devops.atom.task.api

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.atom.task.pojo.QueryData
import com.tencent.bk.devops.atom.task.pojo.QueryNodeInfo
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import com.tencent.bkrepo.common.api.constant.HttpHeaders
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.generic.pojo.TemporaryAccessToken
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.UserRepoCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TokenType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

class DistributionApi : BaseApi() {

    private val httpClient = AtomHttpClient()
    private val fileGateway: String by lazy { SdkEnv.getFileGateway() }
    private val tokenRequest by lazy { fileGateway.isNotBlank() }
    private var createFlag = false
    private var token: String = ""

    fun search(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        metadata: Map<String, String>
    ): List<QueryNodeInfo> {
        val url = if (tokenRequest) {
            token = createToken(userId, projectId)
            "$fileGateway/repository/api/node/search"
        } else {
            "/bkrepo/api/build/repository/api/node/search"
        }
        val queryModel = NodeQueryBuilder().projectId(projectId).repoName(repoName)
            .fullPath(fullPath, OperationType.MATCH)
            .excludeFolder()
            .apply { metadata.forEach { (key, value) -> metadata(key, value) } }
            .page(1, 1000)
            .build()
        logger.debug("queryModel: ${queryModel.toJsonString().replace(System.lineSeparator(), "")}")
        val requestBody = queryModel.toJsonString()
            .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val headers = buildBaseHeaders(userId)
        val request = buildPost(url, requestBody, headers)
        httpClient.doRequest(request).use { response ->
            val responseContent = response.body!!.string()
            val responseData = responseContent.readJsonString<Response<QueryData>>()
            if (!response.isSuccessful) {
                throw AtomException(responseData.message!!)
            }

            val records = responseData.data?.records
            logger.info("match ${records?.size} file: ${records?.map { it.fullPath }}")
            if (records.isNullOrEmpty()) {
                throw AtomException("match zero file to copy")
            }
            return records
        }
    }

    fun search(
        userId: String,
        projectName: String,
        pipelineId: String,
        buildId: String
    ): List<QueryNodeInfo> {
        return try {
            val url = if (tokenRequest) {
                token = createToken(userId, projectName)
                "$fileGateway/repository/api/node/search"
            } else {
                "/bkrepo/api/build/repository/api/node/search"
            }
            val projectRule = Rule.QueryRule("projectId", projectName, OperationType.EQ)
            val repoNameRule = Rule.QueryRule("repoName", "custom", OperationType.EQ)
            val fullPathRule = Rule.QueryRule("fullPath", "/*", OperationType.MATCH)
            val pipelineIdRule = Rule.QueryRule("metadata.pipelineId", pipelineId, OperationType.EQ)
            val buildIdRule = Rule.QueryRule("metadata.buildId", buildId, OperationType.EQ)
            val ruleList = mutableListOf<Rule>(projectRule, repoNameRule, fullPathRule, pipelineIdRule, buildIdRule)
            val rule: Rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
            val headers = buildBaseHeaders(userId)
            val queryModel = QueryModel(
                PageLimit(1, 1000, null, null),
                Sort(Collections.singletonList("fullPath"), Sort.Direction.ASC),
                ArrayList(),
                rule
            )
            val requestBody = queryModel.toJsonString()
                .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
            val request = buildPost(url, requestBody, headers)
            val responseContent: String = request(request, "search node error")
            val response = responseContent.readJsonString<Result<QueryData>>()
            val searchData: QueryData? = response.getData()
            searchData?.records ?: emptyList()
        } catch (e: Exception) {
            throw AtomException("search node error: ${e.message}")
        }
    }

    fun copy(
        userId: String,
        projectId: String,
        srcRepoName: String,
        srcFullPath: String,
        destProjectId: String,
        destRepoName: String,
        destFullPath: String
    ) {
        createProjectOrRepoIfNotExist(userId, projectId)
        createProjectOrRepoIfNotExist(userId, destProjectId)
        val url = if (tokenRequest) {
            token = createToken(userId, projectId)
            "$fileGateway/repository/api/node/copy"
        } else {
            "/bkrepo/api/build/repository/api/node/copy"
        }
        val headers = buildBaseHeaders(userId)
        val copyRequest = NodeMoveCopyRequest(
            srcProjectId = projectId,
            srcRepoName = srcRepoName,
            srcFullPath = srcFullPath,
            destProjectId = destProjectId,
            destRepoName = destRepoName,
            destFullPath = destFullPath,
            overwrite = true,
            operator = userId
        )
        val requestBody = copyRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val request = buildPost(url, requestBody, headers)
        httpClient.doRequest(request).use {
            if (!it.isSuccessful) {
                val responseContent = it.body!!.string()
                val responseData = responseContent.readJsonString<Response<Void>>()
                throw AtomException(responseData.message!!)
            }
        }
        logger.info("copy $srcFullPath to $destFullPath success")
    }

    fun deleteMetadata(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        metadataKeys: Set<String>
    ) {
        val url = if (tokenRequest) {
            "$fileGateway/repository/api/metadata/$projectId/$repoName/$fullPath"
        } else {
            "/bkrepo/api/build/repository/api/metadata/$projectId/$repoName/$fullPath"
        }
        val headers = buildBaseHeaders(userId)
        val requestData: MutableMap<String, Set<String>> = HashMap()
        requestData["keyList"] = metadataKeys
        val requestBody = JsonUtil.toJson<Map<String, Set<String>>>(requestData)
            .toRequestBody(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull())
        val request = buildDelete(url, requestBody, headers)
        try {
            request(request, "delete metadata error")
        } catch (e: Exception) {
            logger.error("delete metadata error, cause:${e.cause}")
            throw AtomException("delete metadata error")
        }
    }

    private fun createToken(userId: String, projectId: String): String {
        if (token.isNotBlank()) {
            return token
        }
        val tokenCreateRequest = TemporaryTokenCreateRequest(
            projectId,
            "custom",
            setOf(StringPool.ROOT),
            setOf(userId),
            emptySet(),
            TimeUnit.DAYS.toSeconds(1),
            null,
            TokenType.ALL
        )
        val request = buildPost(
            "/bkrepo/api/build/generic/temporary/token/create",
            tokenCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(userId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return response.readJsonString<Response<List<TemporaryAccessToken>>>().data!!.first().token
        } else {
            throw AtomException(response)
        }
    }

    private fun createProjectOrRepoIfNotExist(userId: String, projectId: String) {
        if (createFlag) {
            return
        }
        createProject(userId, projectId)
        createRepo(userId, projectId)
        createFlag = true
    }

    private fun createProject(userId: String, projectId: String) {
        val projectCreateRequest = UserProjectCreateRequest(projectId, projectId, "")
        val request = buildPost(
            "$fileGateway/repository/api/project/create",
            projectCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(userId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return
        }
        val code = response.readJsonString<Response<Void>>().code
        if (code == ERROR_PROJECT_EXISTED) {
            return
        } else {
            throw AtomException(response)
        }
    }

    private fun createRepo(userId: String, projectId: String) {
        val repoCreateRequest = UserRepoCreateRequest(
            projectId = projectId,
            name = "custom",
            type = RepositoryType.GENERIC,
            category = RepositoryCategory.LOCAL,
            public = false
        )
        val request = buildPost(
            "$fileGateway/repository/api/repo/create",
            repoCreateRequest.toJsonString().toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()),
            buildBaseHeaders(userId)
        )
        val (status, response) = doRequest(request)
        if (status == 200) {
            return
        }
        val code = response.readJsonString<Response<Void>>().code
        if (code == ERROR_REPO_EXISTED) {
            return
        } else {
            throw AtomException(response)
        }
    }

    private fun buildBaseHeaders(userId: String): MutableMap<String, String> {
        val header = mutableMapOf<String, String>()
        header[HEADER_BKREPO_UID] = userId
        if (token.isNotBlank()) {
            header[HttpHeaders.AUTHORIZATION] = "Temporary $token"
        }

        return header
    }

    private fun doRequest(request: Request, retry: Int = 3): Pair<Int,String> {
        try {
            val response = httpClient.doRequest(request)
            val responseContent = response.body!!.string()
            if (response.isSuccessful) {
                return Pair(response.code, responseContent)
            }
            logger.debug("request url: ${request.url}, code: ${response.code}, response: $responseContent")
            if (response.code > 499 && retry > 0) {
                return doRequest(request, retry - 1)
            }
            return Pair(response.code, responseContent)
        } catch (e: IOException) {
            logger.error("request[${request.url}] error, ", e)
            if (retry > 0) {
                logger.info("retry after 3 seconds")
                Thread.sleep(3 * 1000L)
                return doRequest(request, retry - 1)
            }
            throw e
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DistributionApi::class.java)
        private const val HEADER_BKREPO_UID = "X-BKREPO-UID"

        private const val ERROR_PROJECT_EXISTED = 251005
        private const val ERROR_REPO_EXISTED = 251007
    }

}