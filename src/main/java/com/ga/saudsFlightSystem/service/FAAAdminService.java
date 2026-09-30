package com.ga.saudsFlightSystem.service;

import com.ga.saudsFlightSystem.model.FAAAdmin;
import com.ga.saudsFlightSystem.model.User;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@AllArgsConstructor
public class FAAAdminService {
    private UserService userService;
    public FAAAdmin findFAAAdminByEmailAddress(String email) {
        User user = userService.findUserByEmailAddress(email);
        if (user == null) {
            return null;
        }
        return user.getFaaAdmin();
    }
}
