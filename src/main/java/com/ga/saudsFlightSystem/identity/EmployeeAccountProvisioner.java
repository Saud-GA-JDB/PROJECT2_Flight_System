package com.ga.saudsFlightSystem.identity;

public interface EmployeeAccountProvisioner {
    void checkExistingDetails(String emailAddress, String cpr);

    ProvisionedAccount createAccount(EmployeeAccountRequest request);
}
