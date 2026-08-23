package com.example.customerservice.scheduler;

import com.example.customerservice.service.ChatAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定期删除数据库中没有记录的孤儿附件文件。 */
@Component
@Slf4j
public class ChatAttachmentCleanupScheduler {
    private final ChatAttachmentService attachmentService;
    private final DistributedSchedulerLock schedulerLock;

    public ChatAttachmentCleanupScheduler(
            ChatAttachmentService attachmentService,
            DistributedSchedulerLock schedulerLock
    ) {
        this.attachmentService = attachmentService;
        this.schedulerLock = schedulerLock;
    }

    @Scheduled(
            initialDelayString = "${app.chat.attachment.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${app.chat.attachment.cleanup-delay-ms:86400000}"
    )
    public void cleanup() {
        try {
            schedulerLock.execute("chat-attachment-cleanup", this::cleanupLocked);
        } catch (RuntimeException exception) {
            log.error("孤儿附件清理失败", exception);
        }
    }

    private void cleanupLocked() {
        int removed = attachmentService.cleanupOrphanFiles();
        if (removed > 0) log.info("孤儿附件清理完成，删除文件数={}", removed);
    }
}
