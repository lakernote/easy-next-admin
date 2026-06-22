package com.laker.admin.module.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("wf_process_node")
public class WfProcessNode {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long versionId;
    private String nodeKey;
    private String nodeName;
    private String nodeType;
    private String approveType;
    private String approverType;
    private String approverValue;
    private Boolean allowTransfer;
    private Boolean allowDelegate;
    private Boolean allowAddSign;
    private Boolean allowRemoveSign;
    private Boolean allowReturn;
    private Integer sortOrder;
}
