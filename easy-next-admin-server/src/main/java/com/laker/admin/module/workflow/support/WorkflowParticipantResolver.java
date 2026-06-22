package com.laker.admin.module.workflow.support;

import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.dto.WfParticipantView;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowParticipantResolver {
    private final ISysUserService userService;

    public List<WfParticipantView> resolve(WfProcessInstance instance,
                                           List<WfTask> tasks,
                                           List<WfEvent> events,
                                           List<WfCc> ccList) {
        LinkedHashSet<Long> userIds = collectUserIds(instance, tasks, events, ccList);
        if (userIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> userMap = new LinkedHashMap<>();
        EasyDataScopeContext.ignore(() -> userService.listByIds(userIds))
                .forEach(user -> {
                    if (user.getUserId() != null) {
                        userMap.putIfAbsent(user.getUserId(), user);
                    }
                });
        return userIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(this::toParticipant)
                .toList();
    }

    private LinkedHashSet<Long> collectUserIds(WfProcessInstance instance,
                                               List<WfTask> tasks,
                                               List<WfEvent> events,
                                               List<WfCc> ccList) {
        LinkedHashSet<Long> userIds = new LinkedHashSet<>();
        if (instance != null) {
            addUserId(userIds, instance.getInitiatorId());
            addUserId(userIds, instance.getCreatedBy());
            addUserId(userIds, instance.getUpdatedBy());
        }
        if (tasks != null) {
            tasks.forEach(task -> {
                addUserId(userIds, task.getAssigneeId());
                addUserId(userIds, task.getCreatedBy());
                addUserId(userIds, task.getUpdatedBy());
            });
        }
        if (events != null) {
            events.forEach(event -> {
                addUserId(userIds, event.getOperatorId());
                addUserId(userIds, event.getTargetUserId());
            });
        }
        if (ccList != null) {
            ccList.forEach(cc -> addUserId(userIds, cc.getReceiverId()));
        }
        return userIds;
    }

    private void addUserId(LinkedHashSet<Long> userIds, Long userId) {
        if (userId != null) {
            userIds.add(userId);
        }
    }

    private WfParticipantView toParticipant(SysUser user) {
        return WfParticipantView.builder()
                .name(displayName(user))
                .value(String.valueOf(user.getUserId()))
                .userName(user.getUserName())
                .avatar(user.getAvatar())
                .build();
    }

    private String displayName(SysUser user) {
        String displayName = StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName();
        if (!StringUtils.hasText(displayName)) {
            return "用户 " + user.getUserId();
        }
        if (StringUtils.hasText(user.getUserName()) && !Objects.equals(displayName, user.getUserName())) {
            return "%s（%s）".formatted(displayName, user.getUserName());
        }
        return displayName;
    }
}
