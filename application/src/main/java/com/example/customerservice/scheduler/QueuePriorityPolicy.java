package com.example.customerservice.scheduler;

/**
 * 计算等待队列优先级。
 *
 * <p>VIP 用户按等级获得优先权；普通用户等待达到保障阈值后，会进入
 * 反饥饿保障层，排在所有常规 VIP 等级之前。同一层仍按真实入队时间保持 FIFO。</p>
 */
final class QueuePriorityPolicy {

    static final int MAX_VIP_LEVEL = 5;

    private final long vipPriorityStepMillis;
    private final long antiStarvationMillis;

    QueuePriorityPolicy(
            long vipPriorityStepMillis,
            long antiStarvationMillis
    ) {
        this.vipPriorityStepMillis = Math.max(0L, vipPriorityStepMillis);
        this.antiStarvationMillis = Math.max(1L, antiStarvationMillis);
    }

    double score(
            double enqueuedAt,
            int vipLevel,
            long now
    ) {
        int normalizedVipLevel = Math.max(
                0,
                Math.min(MAX_VIP_LEVEL, vipLevel)
        );
        long waitedMillis = Math.max(0L, now - (long) enqueuedAt);

        if (normalizedVipLevel == 0
                && waitedMillis >= antiStarvationMillis) {
            return enqueuedAt
                    - (double) (MAX_VIP_LEVEL + 1)
                    * vipPriorityStepMillis;
        }

        return enqueuedAt
                - (double) normalizedVipLevel
                * vipPriorityStepMillis;
    }
}
