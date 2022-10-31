package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.task.api.DistributionApi
import com.tencent.bk.devops.atom.task.constant.REPO_CUSTOM
import com.tencent.bk.devops.atom.task.constant.REPO_PIPELINE
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.path.PathUtils
import org.slf4j.LoggerFactory

@AtomService(paramClass = AtomParam::class)
class AcrossProjectDistributionAtom : TaskAtom<AtomParam> {

    /**
     * 执行主入口
     * @param atomContext 插件上下文
     */
    override fun execute(atomContext: AtomContext<AtomParam>) {
        val param = atomContext.param
        val userId = param.pipelineStartUserId
        val projectId = param.projectName
        val pipelineId = param.pipelineId
        val buildId = param.pipelineBuildId
        val customized = param.customized
        var srcPath = param.path
        val destProjectId = param.targetProjectId
        var destPath = param.targetPath
        val cancelDisplay = param.cancelDisplay
        val customPath = param.customPath
        val metadata = getMetadata(param.metadata)

        val repoName: String
        if (!customized) {
            repoName = REPO_PIPELINE
            srcPath = PathUtils.normalizeFullPath("/$pipelineId/$buildId/$srcPath")
        } else {
            repoName = REPO_CUSTOM
            srcPath = PathUtils.normalizeFullPath(srcPath)
        }
        destPath = if (!customPath) {
            PathUtils.normalizeFullPath("/share/$projectId/$destPath")
        } else {
            PathUtils.normalizePath(destPath)
        }

        val fileList = distributionApi.search(userId, projectId, repoName, srcPath, metadata)
        fileList.forEach {
            val destFullPath = PathUtils.normalizeFullPath("$destPath/${it.name}")
            distributionApi.copy(userId, projectId, it.repoName, it.fullPath, destProjectId, REPO_CUSTOM, destFullPath)
        }

        if (cancelDisplay) {
            removeArtifactMetadata(
                userId,
                projectId,
                pipelineId,
                buildId
            )
        }
    }

    private fun removeArtifactMetadata(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String
    ) {
        val nodeInfoList = distributionApi.search(userId, projectId, pipelineId, buildId)
        val metadataKeys: MutableSet<String> = HashSet()
        metadataKeys.add("pipelineId")
        metadataKeys.add("buildId")
        nodeInfoList.forEach {
            distributionApi.deleteMetadata(
                userId,
                projectId,
                REPO_CUSTOM,
                it.fullPath,
                metadataKeys
            )
        }
    }

    private fun getMetadata(strValue: String): Map<String, String> {
        val map = if (strValue.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                val map = mutableMapOf<String, String>()
                strValue.readJsonString<List<Map<String, String>>>()
                    .filterNot { it["key"].isNullOrBlank() }
                    .filterNot { it["value"].isNullOrBlank() }.map {
                        map[it["key"]!!] = it["value"]!!
                    }
                map
            } catch (e: Exception) {
                logger.error("fail to deserialize input: $strValue")
                emptyMap()
            }
        }
        return map
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AcrossProjectDistributionAtom::class.java)
        private val distributionApi = DistributionApi()
    }
}
