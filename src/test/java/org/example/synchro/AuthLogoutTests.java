package org.example.synchro;

import org.example.synchro.resources.AuthResource;
import org.example.synchro.services.CookieService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthLogoutTests {
    @Test
    void logoutExpiresTheHttpOnlySessionCookieEvenWithoutAnActiveSession() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(
                new AuthResource(null, new CookieService(null))).build();

        var response = mvc.perform(post("/auth/logout")).andReturn().getResponse();

        assertEquals(204, response.getStatus());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.startsWith("token_jwt=;"));
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Max-Age=0"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
