package com.example.customerservice.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuePriorityPolicyTest {

    private static final long STEP_MILLIS = 1_000_000L;
    private static final long ANTI_STARVATION_MILLIS = 180_000L;

    private final QueuePriorityPolicy policy =
            new QueuePriorityPolicy(STEP_MILLIS, ANTI_STARVATION_MILLIS);

    @Test
    void vipUserHasPriorityBeforeNormalUserReachesGuaranteeThreshold() {
        long now = 1_000_000L;

        double normalScore = policy.score(now - 60_000L, 0, now);
        double vipScore = policy.score(now, 5, now);

        assertTrue(vipScore < normalScore);
    }

    @Test
    void starvedNormalUserOutranksHighestVipLevel() {
        long now = 1_000_000L;

        double protectedNormalScore = policy.score(
                now - ANTI_STARVATION_MILLIS,
                0,
                now
        );
        double highestVipScore = policy.score(now, 5, now);

        assertTrue(protectedNormalScore < highestVipScore);
    }

    @Test
    void protectedNormalUsersStillUseFifoOrder() {
        long now = 1_000_000L;

        double earlier = policy.score(now - 300_000L, 0, now);
        double later = policy.score(now - 200_000L, 0, now);

        assertTrue(earlier < later);
    }
}
