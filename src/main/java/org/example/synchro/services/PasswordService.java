package org.example.synchro.services;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    @Autowired
    private BCrypt passwordEncoder;

    private final String salt = passwordEncoder.gensalt(12);

    public String hashPassword(String password) {
        return passwordEncoder.hashpw(password, salt);
    }

    public boolean checkPassword(String bDPassword, String password) {
        return passwordEncoder.checkpw(bDPassword, password);
    }
}


