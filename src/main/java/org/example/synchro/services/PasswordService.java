package org.example.synchro.services;

import org.example.synchro.repositories.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

    @Autowired
    private BCrypt BCrypt;

    private static String salt = BCrypt.gensalt(12);

    public String hashPassword(String password) {
        return BCrypt.hashpw(password, salt);
    }

    public boolean checkPassword(String bDPassword, String password) {
        return BCrypt.checkpw(bDPassword, password);
    }
}


