package com.tradepulse.tradepulse;

import com.tradepulse.tradepulse.auth.entity.User;
import com.tradepulse.tradepulse.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class JwtSecretTests {
    private JwtService service(boolean demo) {
        var service=new JwtService();
        ReflectionTestUtils.setField(service,"secretKey","");
        ReflectionTestUtils.setField(service,"demoEnabled",demo);
        ReflectionTestUtils.setField(service,"jwtExpiration",900000L);
        ReflectionTestUtils.invokeMethod(service,"initializeSecret");
        return service;
    }
    @Test void demoTokensWorkOnlyWithTheirOwnStartupKey() {
        var first=service(true);var second=service(true);
        var user=new User();user.setEmail("test@example.com");
        String token=first.generateToken(user);
        assertEquals("test@example.com",first.extractUsername(token));
        assertThrows(io.jsonwebtoken.JwtException.class,()->second.extractUsername(token));
    }
    @Test void nonDemoCannotStartWithAnEmptySecret() {
        assertThrows(IllegalStateException.class,()->service(false));
    }
}
