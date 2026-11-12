package com.ga.maintenance.client;

import com.ga.common.security.client.IdentityClientConfiguration;
import com.ga.maintenance.model.request.EmployeeAccountRequest;
import com.ga.maintenance.model.request.response.AccountSnapshot;
import com.ga.maintenance.model.request.response.ProvisionedAccount;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "identity", url = "${identity.base-url}", configuration = IdentityClientConfiguration.class)
public interface IdentityServiceClient extends com.ga.common.security.client.IdentityServiceClient {
    @GetMapping("/internal/identity/accounts/{id}")
    AccountSnapshot account(@PathVariable("id") Long id);

    @PostMapping("/internal/identity/accounts/batch")
    Map<Long, AccountSnapshot> accounts(@RequestBody List<Long> ids);

    @GetMapping("/internal/identity/accounts/by-email")
    AccountSnapshot accountByEmail(@RequestParam("email") String email);

    record ExistingDetails(String emailAddress, String cpr) {}

    @PostMapping("/internal/identity/accounts/check-details")
    void checkDetails(@RequestBody ExistingDetails request);

    @PostMapping("/internal/identity/employees")
    ProvisionedAccount create(@RequestBody EmployeeAccountRequest request);

    @PostMapping("/internal/identity/employees/{key}/activate")
    void activate(@PathVariable("key") String key, @RequestParam("userId") Long userId);

    @DeleteMapping("/internal/identity/employees/{key}")
    void cancel(@PathVariable("key") String key);
}
