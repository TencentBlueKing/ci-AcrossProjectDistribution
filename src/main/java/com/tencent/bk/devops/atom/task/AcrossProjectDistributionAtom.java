package com.tencent.bk.devops.atom.task;


import com.fasterxml.jackson.core.type.TypeReference;
import com.tencent.bk.devops.atom.AtomContext;
import com.tencent.bk.devops.atom.common.Status;
import com.tencent.bk.devops.atom.pojo.AtomResult;
import com.tencent.bk.devops.atom.pojo.Result;
import com.tencent.bk.devops.atom.spi.AtomService;
import com.tencent.bk.devops.atom.spi.TaskAtom;
import com.tencent.bk.devops.atom.task.api.HttpUtil;
import com.tencent.bk.devops.atom.task.pojo.ArtifactoryType;
import com.tencent.bk.devops.atom.task.pojo.AtomParam;
import com.tencent.bk.devops.atom.task.pojo.Count;
import com.tencent.bk.devops.atom.utils.json.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * @version 1.0
 */
@AtomService(paramClass = AtomParam.class)
public class AcrossProjectDistributionAtom implements TaskAtom<AtomParam> {

    private static final Logger logger = LoggerFactory.getLogger(AcrossProjectDistributionAtom.class);
    private HttpUtil httpUtil = new HttpUtil();


    /**
     * 执行主入口
     * @param atomContext 插件上下文
     */
    @Override
    public void execute(AtomContext<AtomParam> atomContext) {
        AtomParam param = atomContext.getParam();
        AtomResult result = atomContext.getResult();
        String projectId = param.getProjectName();
        String pipelineId = param.getPipelineId();
        String buildId = param.getPipelineBuildId();
        String customized = param.getCustomized();
        String relativePath = param.getPath();
        String targetProjectId = param.getTargetProjectId();
        String targetPath = "/";
        ArtifactoryType artifactoryType = ArtifactoryType.CUSTOM_DIR;
        if(param.getTargetPath()!=null) targetPath = param.getTargetPath();
        if("false".equals(customized)){
            artifactoryType = ArtifactoryType.PIPELINE;
            if(relativePath.startsWith("/")) relativePath = relativePath.substring(1);
            relativePath = new StringBuilder("/").append(pipelineId).append("/").append(buildId).append("/").append(relativePath).toString();
        }
        try {
            relativePath = URLEncoder.encode(relativePath, "utf-8");
            targetPath = URLEncoder.encode(targetPath, "utf-8");
        } catch (UnsupportedEncodingException e) {
            result.setStatus(Status.error);
            result.setMessage(e.getMessage());
            return;
        }
        String url = "/artifactory/api/build/artifactories/artifactoryType/{artifactoryType}/acrossProjectCopy?path={path}&targetProjectId={targetProjectId}&targetPath={targetPath}";
        url = url.replace("{artifactoryType}", artifactoryType+"")
                .replace("{path}", relativePath)
                .replace("{targetProjectId}", targetProjectId)
                .replace("{targetPath}", targetPath);
        try {
            String response = httpUtil.doGet(url);
            if("false".equals(response)){
                result.setStatus(Status.error);
                result.setMessage("visit AcrossProject failured...");
                return;
            }
            Result<Count> countResult = JsonUtil.fromJson(response, new TypeReference<Result<Count>>() {
            });
            if(countResult.getStatus()!=0){
                result.setStatus(Status.error);
                result.setMessage("visit AcrossProject failured...");
                return;
            }
            result.setStatus(Status.success);
            result.setMessage("Across Project Distribution success, copy file count: " + countResult.getData());
        } catch (IOException e) {
            result.setStatus(Status.error);
            result.setMessage("visit AcrossProject exception..."+e.getMessage());
        }
    }

}
