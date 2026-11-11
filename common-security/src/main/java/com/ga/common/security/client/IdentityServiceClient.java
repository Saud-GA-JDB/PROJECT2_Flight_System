package com.ga.common.security.client;

import com.ga.common.security.CurrentActor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity", url = "${identity.base-url}", configuration = IdentityClientConfiguration.class)
public interface IdentityServiceClient {

    @PostMapping("/internal/identity/authenticate")
    CurrentActor authenticate(@RequestHeader("Authorization") String authorization);
}
