package org.example.synchro.services;

import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordService {

    private final BCrypt passwordEncoder = new BCrypt();

    private final String salt = passwordEncoder.gensalt(12);

    public String hashPassword(String password) {
        return passwordEncoder.hashpw(password, salt);
    }

    public boolean checkPassword(String bDPassword, String password) {
        return passwordEncoder.checkpw(bDPassword, password);
    }
}


