package com.example.customerservice.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupportTicketStatusTest {

    @Test
    void resolvedTicketCanReturnToInProgress() {
        assertTrue(SupportTicketStatus.RESOLVED.canTransitionTo(SupportTicketStatus.IN_PROGRESS));
    }

    @Test
    void ticketCannotTransitionToTheSameStatus() {
        assertFalse(SupportTicketStatus.OPEN.canTransitionTo(SupportTicketStatus.OPEN));
    }
}
