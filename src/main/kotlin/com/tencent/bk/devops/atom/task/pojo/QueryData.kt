package com.tencent.bk.devops.atom.task.pojo

data class QueryData(
    var count: Int,
    var page: Int,
    var pageSize: Int,
    var totalPages: Int,
    var records: List<QueryNodeInfo>
)
