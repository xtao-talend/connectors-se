package org.talend.components.workday.datastore;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

class TokenTest {

    @Test
    void isTooOld() {
        Token token = new Token("accesToken", "bearer", Instant.now());
        Assertions.assertTrue(token.isTooOld());

        token = new Token("accesToken", "bearer", Instant.now().plus(20, ChronoUnit.SECONDS));
        Assertions.assertTrue(token.isTooOld());

        Token token1 = new Token("accesToken", "bearer", Instant.now().minus(120, ChronoUnit.MINUTES));
        Assertions.assertTrue(token1.isTooOld());

        Token token2 = new Token("accesToken", "bearer", Instant.now().plus(120, ChronoUnit.MINUTES));
        Assertions.assertFalse(token2.isTooOld());
    }
}