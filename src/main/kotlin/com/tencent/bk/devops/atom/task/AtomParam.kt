package com.tencent.bk.devops.atom.task

import com.tencent.bk.devops.atom.pojo.AtomBaseParam

class AtomParam : AtomBaseParam() {
    val customized: Boolean = false
    val path: String = ""
    val metadata: String = ""
    val targetProjectId: String = ""
    val targetPath: String = ""
    val cancelDisplay: Boolean = false
}
