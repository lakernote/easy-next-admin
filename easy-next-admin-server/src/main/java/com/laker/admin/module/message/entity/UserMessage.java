package com.laker.admin.module.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_message")
public class UserMessage {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long receiverId;
    private Long senderId;
    @TableField(exist = false)
    private String senderName;
    private String title;
    private String content;
    private String category;
    private String level;
    private String bizType;
    private String bizId;
    private String link;
    private Integer readStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
