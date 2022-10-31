package com.tencent.bk.devops.atom.task.pojo

data class QueryNodeInfo(
    var createdBy: String,
    var createdDate: String,
    var lastModifiedBy: String,
    var lastModifiedDate: String,
    var folder: Boolean,
    var path: String,
    var name: String,
    var fullPath: String,
    var size: Long,
    var sha256: String? = null,
    var md5: String? = null,
    var projectId: String,
    var repoName: String,
    var metadata: Map<String, String>? = null
)
