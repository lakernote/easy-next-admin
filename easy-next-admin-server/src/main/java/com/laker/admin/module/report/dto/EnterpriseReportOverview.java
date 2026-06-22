package com.laker.admin.module.report.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EnterpriseReportOverview {
    private String organizationName;
    private String reportPeriod;
    private String generatedAt;
    private String preparedBy;
    private String dataScopeLabel;
    private OrganizationLedgerReport organizationLedger;
    private PurchaseReviewReport purchaseReview;

    @Data
    public static class OrganizationLedgerReport {
        private String reportNo;
        private List<ReportMetric> metrics;
        private List<DepartmentLedgerRow> rows;
        private List<SignatureCell> signatures;
    }

    @Data
    public static class PurchaseReviewReport {
        private String reportNo;
        private List<ReportMetric> metrics;
        private List<PurchaseReviewRow> rows;
        private List<SignatureCell> signatures;
    }

    @Data
    public static class ReportMetric {
        private String label;
        private String value;
    }

    @Data
    public static class DepartmentLedgerRow {
        private Integer index;
        private String departmentName;
        private String leaderName;
        private Integer userCount;
        private Integer enabledCount;
        private String managerSummary;
        private String positionSummary;
        private String lastLoginSummary;
    }

    @Data
    public static class PurchaseReviewRow {
        private Integer index;
        private String requestNo;
        private String applicantName;
        private String departmentName;
        private String itemName;
        private String category;
        private Integer quantity;
        private BigDecimal estimatedAmount;
        private String requiredDate;
        private String statusText;
        private String currentNodeName;
    }

    @Data
    public static class SignatureCell {
        private String label;
        private String value;
    }
}
