package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.laker.admin.common.util.EasyTreeUtil;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuVo implements EasyTreeUtil.TreeNode<MenuVo> {
    private Long id;

    private Long pid;

    private String title;

    private String icon;

    private String href;

    private Integer sort;

    private Boolean enable;

    private Boolean visible;

    private Integer type;

    private String powerCode;

    private String componentPath;

    private List<MenuVo> children;
}
