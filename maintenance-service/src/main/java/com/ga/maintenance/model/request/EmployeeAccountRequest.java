package com.ga.maintenance.model.request;

public record EmployeeAccountRequest(
        AccountType accountType,
        Long employeeId,
        String emailAddress,
        String password,
        String securityQuestion,
        String normalizedSecurityAnswer,
        String fName,
        String lName,
        String cpr,
        String phoneNumberOpeningCode,
        String phoneNumber
) {
    public enum AccountType {
        MAINTENANCE_EMPLOYEE
    }

    @Override
    public String toString() {
        return "EmployeeAccountRequest[accountType=" + accountType
                + ", employeeId=" + employeeId + "]";
    }
}
