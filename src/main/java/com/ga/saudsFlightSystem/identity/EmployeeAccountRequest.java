package com.ga.saudsFlightSystem.identity;

public record EmployeeAccountRequest(
        AccountType accountType,
        Long employeeId,
        String emailAddress,
        String password,
        String securityQuestion,
        String normalizedSecurityAnswer
) {
    public enum AccountType {
        AIRLINE_EMPLOYEE,
        AIRPORT_EMPLOYEE,
        MAINTENANCE_EMPLOYEE,
        ATC_EMPLOYEE
    }

    @Override
    public String toString() {
        return "EmployeeAccountRequest[accountType=" + accountType
                + ", employeeId=" + employeeId + "]";
    }
}
