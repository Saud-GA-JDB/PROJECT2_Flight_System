package com.ga.saudsFlightSystem.identity;

import com.ga.saudsFlightSystem.security.CurrentActor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name = "identity", url = "${identity.base-url}", configuration = IdentityClientConfiguration.class)
public interface IdentityServiceClient {
    @GetMapping("/internal/identity/accounts/{id}")
    AccountSnapshot account(@PathVariable("id") Long id);

    @PostMapping("/internal/identity/accounts/batch")
    Map<Long, AccountSnapshot> accounts(@RequestBody List<Long> ids);

    @GetMapping("/internal/identity/accounts/by-email")
    AccountSnapshot accountByEmail(@RequestParam("email") String email);

    @PostMapping("/internal/identity/profiles/batch")
    Map<Long, PersonalProfile> profiles(@RequestBody List<Long> ids);

    record ExistingDetails(String emailAddress, String cpr) {}

    @PostMapping("/internal/identity/accounts/check-details")
    void checkDetails(@RequestBody ExistingDetails request);

    @PostMapping("/internal/identity/employees")
    ProvisionedAccount create(@RequestBody EmployeeAccountRequest request);

    @PostMapping("/internal/identity/employees/{key}/activate")
    void activate(@PathVariable("key") String key, @RequestParam("userId") Long userId);

    @DeleteMapping("/internal/identity/employees/{key}")
    void cancel(@PathVariable("key") String key);

    @PostMapping("/internal/identity/authenticate")
    CurrentActor authenticate(@RequestHeader("Authorization") String authorization);
}
