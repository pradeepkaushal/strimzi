/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.controller.topic;


import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BackOffTest {

    @Test
    public void testDefaultBackoff() {
        BackOff b = new BackOff();
        assertEquals(0L, b.delayMs());
        assertEquals(200L, b.delayMs());
        assertEquals(400L, b.delayMs());
        assertEquals(800L, b.delayMs());
        try {
            b.delayMs();
            fail("Should throw");
        } catch (MaxAttemptsExceededException e) {

        }
        assertEquals(1400L, b.totalDelayMs());
    }

    @Test
    public void testAnotherBackoff() {
        BackOff b = new BackOff(1, 10, 5);
        assertEquals(0L, b.delayMs());
        assertEquals(1L, b.delayMs());
        assertEquals(10L, b.delayMs());
        assertEquals(100L, b.delayMs());
        assertEquals(1000L, b.delayMs());
        try {
            b.delayMs();
            fail("Should throw");
        } catch (MaxAttemptsExceededException e) {

        }

        assertEquals(1111L, b.totalDelayMs());
    }
}