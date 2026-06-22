package com.laker.admin.module.system.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserImportRowError {
    private int rowNumber;
    private String userName;
    private String message;
}
