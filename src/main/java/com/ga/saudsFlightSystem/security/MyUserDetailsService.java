package com.ga.saudsFlightSystem.security;

import com.ga.saudsFlightSystem.model.User;
import com.ga.saudsFlightSystem.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyUserDetailsService implements UserDetailsService {
    private UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userService.findUserByEmailAddress(email);
        if (user == null) {
            throw new UsernameNotFoundException("User with this email address does not exist.");
        }
        return new MyUserDetails(user);
    }
}
