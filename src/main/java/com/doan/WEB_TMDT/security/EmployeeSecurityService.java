package com.doan.WEB_TMDT.security;

import com.doan.WEB_TMDT.module.auth.entity.Employee;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("employeeSecurityService")
@RequiredArgsConstructor
public class EmployeeSecurityService {

    private final UserRepository userRepository;

    public boolean hasPosition(Authentication authentication, String position) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null || user.getEmployee() == null) {
            return false;
        }

        Employee employee = user.getEmployee();
        return employee.getPosition() != null && 
               employee.getPosition().name().equals(position);
    }
}
