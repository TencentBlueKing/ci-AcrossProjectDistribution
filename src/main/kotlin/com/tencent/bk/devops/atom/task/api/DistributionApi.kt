package com.tencent.bk.devops.atom.task.api

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.exception.AtomException
import com.tencent.bk.devops.atom.pojo.Result
import com.tencent.bk.devops.atom.task.pojo.QueryData
import com.tencent.bk.devops.atom.task.pojo.QueryNodeInfo
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import com.tencent.bkrepo.common.api.constant.MediaTypes
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.repository.pojo.node.service.NodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.util.Collections

class DistributionApi : BaseApi() {

    private val httpClient = AtomHttpClient()

    fun search(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        metadata: Map<String, String>
    ): List<QueryNodeInfo> {
        val url = "/bkrepo/api/build/repository/api/node/search"
        val queryModel = NodeQueryBuilder().projectId(projectId).repoName(repoName)
            .fullPath(fullPath, OperationType.MATCH)
            .excludeFolder()
            .apply { metadata.forEach { (key, value) -> metadata(key, value) } }
            .page(1, 1000)
            .build()
        logger.debug("queryModel: ${queryModel.toJsonString().replace(System.lineSeparator(), "")}")
        val requestBody = RequestBody.create(
            MediaTypes.APPLICATION_JSON.toMediaTypeOrNull(),
            queryModel.toJsonString()
        )
        val headers: MutableMap<String, String> = HashMap()
        headers[HEADER_BKREPO_UID] = userId
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
            val url = "/bkrepo/api/build/repository/api/node/search"
            val projectRule = Rule.QueryRule("projectId", projectName, OperationType.EQ)
            val repoNameRule = Rule.QueryRule("repoName", "custom", OperationType.EQ)
            val fullPathRule = Rule.QueryRule("fullPath", "/*", OperationType.MATCH)
            val pipelineIdRule = Rule.QueryRule("metadata.pipelineId", pipelineId, OperationType.EQ)
            val buildIdRule = Rule.QueryRule("metadata.buildId", buildId, OperationType.EQ)
            val ruleList = mutableListOf<Rule>(projectRule, repoNameRule, fullPathRule, pipelineIdRule, buildIdRule)
            val rule: Rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)
            val headers: MutableMap<String, String> = HashMap()
            headers[HEADER_BKREPO_UID] = userId
            val queryModel = QueryModel(
                PageLimit(1, 1000, null, null),
                Sort(Collections.singletonList("fullPath"), Sort.Direction.ASC),
                ArrayList(),
                rule
            )
            val requestBody = RequestBody.create(
                MediaTypes.APPLICATION_JSON.toMediaTypeOrNull(),
                queryModel.toJsonString()
            )
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
        val url = "/bkrepo/api/build/repository/api/node/copy"
        val headers = mutableMapOf(HEADER_BKREPO_UID to userId)
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
        val requestBody = RequestBody.create(MediaTypes.APPLICATION_JSON.toMediaTypeOrNull(), copyRequest.toJsonString())
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
        val url = ("/bkrepo/api/build/repository/api/metadata/$projectId/$repoName/$fullPath")
        val headers: MutableMap<String, String> = HashMap()
        headers[HEADER_BKREPO_UID] = userId
        val requestData: MutableMap<String, Set<String>> = HashMap()
        requestData["keyList"] = metadataKeys
        val requestBody = RequestBody.create(
            MediaTypes.APPLICATION_JSON.toMediaTypeOrNull(),
            JsonUtil.toJson<Map<String, Set<String>>>(requestData)
        )
        val request = buildDelete(url, requestBody, headers)
        try {
            request(request, "delete metadata error")
        } catch (e: Exception) {
            logger.error("delete metadata error, cause:${e.cause}")
            throw AtomException("delete metadata error")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DistributionApi::class.java)
        private const val HEADER_BKREPO_UID = "X-BKREPO-UID"
    }

}