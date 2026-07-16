import type { Env, LLMQueueMessage, SMSQueueMessage, InferenceRequest } from "../types";
import { completeStructured, AuthError, BadRequestError } from "../inference/openrouter";
import { getThreadOrNull, transitionState } from "../db/threads";
import { getPersonaOrNull } from "../db/personas";
import { createMessage } from "../db/messages";
import { writeAuditLog } from "../db/audit";
import { enqueueOutboundSms, getNextSequence } from "../db/outbound-sms";

// LLM Queue Consumer handler
export async function handleLlmQueue(
  batch: MessageBatch<LLMQueueMessage>,
  env: Env,
): Promise<void> {
  for (const message of batch.messages) {
    const data = message.body;

    try {
      await processLlmJob(data, env);
      message.ack();
    } catch (err) {
      console.error("LLM queue processing error:", err);

      // Check if this is a non-retryable error
      if (err instanceof AuthError || err instanceof BadRequestError) {
        // Don't retry - these won't succeed
        await writeAuditLog(env.DB, {
          threadId: data.threadId,
          action: "llm_call_failed",
          actor: "worker:persona",
          details: {
            error: err.message,
            errorType: err.name,
            nonRetryable: true,
          },
        });

        // Escalate the thread
        await escalateThread(env, data.threadId, "llm_non_retryable_error");
        message.ack(); // Don't retry, message goes to DLQ by Cloudflare if max_retries hit
      } else {
        // Retryable error - let Cloudflare retry
        message.retry();
      }
    }
  }
}

async function processLlmJob(data: LLMQueueMessage, env: Env): Promise<void> {
  const thread = await getThreadOrNull(env.DB, data.threadId);
  if (!thread) {
    await writeAuditLog(env.DB, {
      threadId: data.threadId,
      action: "llm_call_failed",
      actor: "worker:persona",
      details: { error: "Thread not found" },
    });
    return;
  }

  // Load persona
  const persona = await getPersonaOrNull(env.DB, data.personaId);
  if (!persona) {
    await writeAuditLog(env.DB, {
      threadId: data.threadId,
      action: "llm_call_failed",
      actor: "worker:persona",
      details: { error: "Persona not found" },
    });
    return;
  }

  // Build inference request with persona system prompt
  const systemPrompt = buildPersonaPrompt(persona);
  const inferenceRequest: InferenceRequest = {
    model: env.OPENROUTER_MODEL ?? "meta-llama/llama-3.3-70b-instruct:free",
    messages: [
      { role: "system", content: systemPrompt },
      { role: "user", content: data.body },
    ],
    temperature: 0.7,
    maxTokens: 500,
  };

  // Call OpenRouter with structured output schema
  const personaSchema = {
    name: "persona_response",
    schema: {
      type: "object",
      properties: {
        responseText: { type: "string", minLength: 1 },
        confidence: { type: "number", minimum: 0.0, maximum: 1.0 },
        metadata: { type: "object" },
      },
      required: ["responseText", "confidence"],
      additionalProperties: false,
    },
  };

  const response = await completeStructured(inferenceRequest, personaSchema, env.OPENROUTER_API_KEY, {
    url: env.OPENROUTER_URL,
    model: env.OPENROUTER_MODEL,
    fallbackModel: env.OPENROUTER_FALLBACK_MODEL,
  });

  // Extract response text
  const structured = response.structuredContent as { responseText?: string; confidence?: number } | null;
  const responseText = structured?.responseText ?? response.content;
  const confidence = structured?.confidence ?? response.confidence;

  if (!responseText || responseText.trim() === "") {
    throw new Error("Empty response from LLM");
  }

  // Create outbound message in D1
  const outboundMessage = await createMessage(env.DB, {
    id: crypto.randomUUID(),
    threadId: data.threadId,
    direction: "outbound",
    body: responseText,
    timestamp: new Date().toISOString(),
    worker: "persona",
    confidence,
  });

  // Write audit log for LLM call
  await writeAuditLog(env.DB, {
    threadId: data.threadId,
    action: "llm_call",
    actor: "worker:persona",
    details: {
      model: response.model,
      tokensUsed: response.tokensUsed,
      confidence: response.confidence,
      success: true,
      threadId: data.threadId,
      latencyMs: response.latencyMs,
    },
  });

  // Enqueue SMS for delivery (with delay)
  const delaySeconds = Math.floor(calculateDelay(thread.state) / 1000);
  const sequence = await getNextSequence(env.DB, data.threadId);

  // Store outbound SMS for polling
  await enqueueOutboundSms(env.DB, {
    id: crypto.randomUUID(),
    threadId: data.threadId,
    messageId: outboundMessage.id,
    deviceId: "default", // Will be resolved from device_keys
    body: responseText,
    phoneNumber: data.clientPhone,
    scheduledFor: new Date(Date.now() + delaySeconds * 1000).toISOString(),
    sequence,
  });

  // Also enqueue to SMS queue for delivery scheduling
  const smsMessage: SMSQueueMessage = {
    threadId: data.threadId,
    messageId: outboundMessage.id,
    body: responseText,
    phoneNumber: data.clientPhone,
    deviceId: "default",
    delaySeconds,
    sequence,
  };

  try {
    await env.SMS_QUEUE.send(smsMessage);
  } catch {
    // SMS queue failure - log but don't fail the LLM processing
    await writeAuditLog(env.DB, {
      threadId: data.threadId,
      action: "sms_queue_failed",
      actor: "system",
      details: { messageId: outboundMessage.id },
    });
  }
}

// Build persona system prompt
function buildPersonaPrompt(persona: {
  name: string;
  tone: string;
  vocabulary: string[];
  offerings: string[];
  depositWording: string | null;
  boundaries: string[] | null;
  availabilityPolicy: unknown;
}): string {
  const parts: string[] = [
    `You are ${persona.name}.`,
    `Tone: ${persona.tone}.`,
  ];

  if (persona.vocabulary.length > 0) {
    parts.push(`Vocabulary: ${persona.vocabulary.join(", ")}.`);
  }

  if (persona.offerings.length > 0) {
    parts.push(`Services offered: ${persona.offerings.join(", ")}.`);
  }

  if (persona.depositWording) {
    parts.push(`Deposit request wording: ${persona.depositWording}`);
  }

  if (persona.boundaries && persona.boundaries.length > 0) {
    parts.push(`Boundaries (do not discuss): ${persona.boundaries.join(", ")}.`);
  }

  parts.push("Respond naturally as a real person via SMS. Keep responses under 320 characters.");
  parts.push("Never reveal you are an AI. Never use corporate or customer service language.");

  return parts.join(" ");
}

// Calculate response delay (simulated TimingWorker)
function calculateDelay(state: string): number {
  if (state === "NEW" || state === "GREETING") {
    // Initial response: 45-180s
    return 45000 + Math.floor(Math.random() * 135000);
  }
  // Follow-up: 20-90s
  return 20000 + Math.floor(Math.random() * 70000);
}

// Escalate thread on failure
async function escalateThread(env: Env, threadId: string, reason: string): Promise<void> {
  const thread = await getThreadOrNull(env.DB, threadId);
  if (!thread) return;

  try {
    await transitionState(env.DB, threadId, "ESCALATED", thread.revision, "system", {
      reason,
    });
  } catch {
    // Transition may fail if already escalated or in terminal state
  }

  await writeAuditLog(env.DB, {
    threadId,
    action: "owner_alert",
    actor: "system",
    details: { reason: "llm_failure", threadId },
  });
}
