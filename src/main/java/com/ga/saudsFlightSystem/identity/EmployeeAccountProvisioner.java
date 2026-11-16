package com.ga.saudsFlightSystem.identity;

import com.ga.common.contract.identity.ProvisionedAccount;

public interface EmployeeAccountProvisioner {
    void checkExistingDetails(String emailAddress, String cpr);

    ProvisionedAccount createAccount(EmployeeAccountRequest request);
}
