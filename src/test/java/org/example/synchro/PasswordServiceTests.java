package org.example.synchro;

import org.example.synchro.services.PasswordService;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTests {
    @Test
    void passwordRemainsValidAfterServiceRestart() {
        String savedHash = new PasswordService().hashPassword("minha-senha-123");
        var restarted = new PasswordService();

        assertTrue(restarted.checkPassword(savedHash, "minha-senha-123"));
        assertFalse(restarted.checkPassword(savedHash, "outra-senha-123"));
    }

    @Test
    void existingHashesWithTheOldSharedSaltStillWork() {
        String oldSalt = BCrypt.gensalt(12);
        String existingHash = BCrypt.hashpw("senha-antiga-123", oldSalt);

        assertTrue(new PasswordService().checkPassword(existingHash, "senha-antiga-123"));
    }

    @Test
    void eachNewPasswordHashUsesItsOwnSalt() {
        var passwords = new PasswordService();
        assertNotEquals(passwords.hashPassword("mesma-senha-123"),
                passwords.hashPassword("mesma-senha-123"));
    }

    @Test
    void invalidStoredHashesAndMissingPasswordsAreRejected() {
        var passwords = new PasswordService();
        assertFalse(passwords.checkPassword(null, "senha-123"));
        assertFalse(passwords.checkPassword("invalid-hash", "senha-123"));
        assertFalse(passwords.checkPassword("", "senha-123"));
        assertFalse(passwords.checkPassword("invalid-hash", null));
    }
}
