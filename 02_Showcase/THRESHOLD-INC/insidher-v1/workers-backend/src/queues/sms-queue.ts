import type { Env, SMSQueueMessage } from "../types";
import { writeAuditLog } from "../db/audit";

// SMS Queue Consumer handler
export async function handleSmsQueue(
  batch: MessageBatch<SMSQueueMessage>,
  env: Env,
): Promise<void> {
  for (const message of batch.messages) {
    const data = message.body;

    try {
      // The SMS consumer marks the message as ready for delivery via polling
      // The actual SMS sending is done by the Android SmsSender via polling
      // This consumer is mainly for tracking and retry logic

      await writeAuditLog(env.DB, {
        threadId: data.threadId,
        action: "sms_delivered",
        actor: "system",
        details: {
          messageId: data.messageId,
          phoneNumber: data.phoneNumber,
          delaySeconds: data.delaySeconds,
          sequence: data.sequence,
        },
      });

      message.ack();
    } catch (err) {
      console.error("SMS queue processing error:", err);
      message.retry();
    }
  }
}
