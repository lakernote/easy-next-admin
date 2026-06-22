package com.laker.admin.infrastructure.web.mvc;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 通用分页查询请求。
 *
 * <p>使用示例：
 * {@code ?page=1&size=10&sort=createdAt|desc&filter=username|like|admin}。
 * 控制器参数必须使用 {@link PageQuery} 声明允许字段，字段会由
 * {@link PageRequestArgumentResolver} 映射到安全的服务端列名。</p>
 */
@Builder
@ToString
@Getter
@Schema(description = "通用分页、排序和过滤查询参数", example = "?page=1&size=10&sort=createdAt|desc&filter=username|like|admin")
public class PageRequest {
    @Schema(description = "页码，从1开始，默认1")
    private int page;
    @Schema(description = "每页大小，默认10")
    private int size;

    @JsonIgnore
    private QueryWrapper<?> queryWrapper;

    public <T> Page<T> toPage() {
        return new Page<>(page, size);
    }
}
