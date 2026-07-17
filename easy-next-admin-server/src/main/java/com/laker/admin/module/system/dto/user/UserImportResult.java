package com.laker.admin.module.system.dto.user;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserImportResult {
    private int totalRows;
    private int successRows;
    private int failedRows;
    private List<UserImportRowError> errors = new ArrayList<>();

    public void markSuccess() {
        successRows++;
    }

    public void addError(int rowNumber, String userName, String message) {
        failedRows++;
        errors.add(new UserImportRowError(rowNumber, userName, message));
    }
}
