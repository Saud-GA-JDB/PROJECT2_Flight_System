package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.identity.IdentityServiceClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtRequestFilter extends OncePerRequestFilter {
    private final IdentityServiceClient identity;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                CurrentActor actor = identity.authenticate(authorization);
                var authorities = actor.active() && "ACTIVE".equals(actor.status())
                        ? List.of(new SimpleGrantedAuthority("ACCOUNT_ACTIVE")) : List.<SimpleGrantedAuthority>of();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, authorities));
            } catch (BadCredentialsException ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please login with a valid token");
                return;
            } catch (RuntimeException ex) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Identity is unavailable. Please retry shortly.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
