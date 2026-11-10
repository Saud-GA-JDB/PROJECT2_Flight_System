package com.ga.saudsFlightSystem.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.logging.Logger;

@Service
@RequiredArgsConstructor
public class RemoteEmployeeAccountProvisioner implements EmployeeAccountProvisioner {
    private final IdentityClient client;
    private static final Logger logger = Logger.getLogger(RemoteEmployeeAccountProvisioner.class.getName());

    @Override
    public void checkExistingDetails(String emailAddress, String cpr) {
        client.checkDetails(new IdentityClient.ExistingDetails(emailAddress, cpr));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProvisionedAccount createAccount(EmployeeAccountRequest request) {
        String key = request.accountType().name() + ":" + request.employeeId();
        var created = new java.util.concurrent.atomic.AtomicReference<ProvisionedAccount>();
        // Register before HTTP: a timeout may happen after Identity has saved the account.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == STATUS_COMMITTED) {
                        // The committed link is also picked up by PendingEmployeeActivation.
                        if (created.get() != null) client.activate(key, created.get().userId());
                    } else if (status == STATUS_ROLLED_BACK) {
                        client.cancel(key);
                    }
                } catch (RuntimeException ex) {
                    logger.warning("Account provisioning needs recovery for " + key);
                }
            }

        });
        ProvisionedAccount account = client.create(request);
        created.set(account);
        return account;
    }
}
