package com.example.customerservice.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssignResultTest {

    @Test
    void waitingResultContainsPositionAndEta() {
        AssignResult result = AssignResult.waiting(3L, 600L);

        assertEquals(AssignResult.WAITING, result.getAssignmentStatus());
        assertEquals(3L, result.getWaitingPosition());
        assertEquals(600L, result.getEstimatedWaitSeconds());
    }

    @Test
    void legacyWaitingFactoryKeepsEtaOptional() {
        AssignResult result = AssignResult.waiting(1L);

        assertEquals(1L, result.getWaitingPosition());
        assertNull(result.getEstimatedWaitSeconds());
    }
}
