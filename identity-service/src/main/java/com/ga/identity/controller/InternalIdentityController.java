package com.ga.identity.controller;

import com.ga.common.web.exception.InformationNotFoundException;
import com.ga.identity.service.AccountService;
import com.ga.identity.service.EmployeeAccountService;
import com.ga.identity.service.UserProfileService;
import com.ga.identity.model.request.*;
import com.ga.identity.model.request.response.*;
import com.ga.identity.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/identity")
@RequiredArgsConstructor
public class InternalIdentityController {
    private final AccountService accounts;
    private final UserProfileService profiles;
    private final EmployeeAccountService provisioner;

    @GetMapping("/accounts/{id}")
    public AccountSnapshot account(@PathVariable Long id) {
        return accounts.findAccountStateById(id).orElseThrow(() -> new InformationNotFoundException("Account not found."));
    }

    @PostMapping("/accounts/batch")
    public Map<Long, AccountSnapshot> accounts(@RequestBody List<Long> ids) {
        return accounts.findAccountsByIds(ids);
    }

    @GetMapping("/accounts/by-email")
    public AccountSnapshot accountByEmail(@RequestParam String email) {
        return accounts.findByEmailAddress(email);
    }

    @PostMapping("/profiles/batch")
    public Map<Long, PersonalProfile> profiles(@RequestBody List<Long> ids) {
        return profiles.findByUserIds(ids);
    }

    @PostMapping("/accounts/check-details")
    public void checkDetails(@RequestBody ExistingDetailsRequest request) {
        provisioner.checkExistingDetails(request.emailAddress(), request.cpr());
    }

    @PostMapping("/employees")
    public ProvisionedAccount create(@RequestBody EmployeeAccountRequest request) {
        return provisioner.createAccount(request);
    }

    @PostMapping("/employees/{key}/activate")
    public void activate(@PathVariable String key, @RequestParam Long userId) {
        provisioner.activate(key, userId);
    }

    @DeleteMapping("/employees/{key}")
    public void cancel(@PathVariable String key) {
        provisioner.cancel(key);
    }

    @PostMapping("/authenticate")
    public CurrentActor authenticate(@RequestHeader("Authorization") String authorization) {
        return accounts.authenticate(authorization);
    }
}
