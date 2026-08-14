import type { InferenceRequest, InferenceResponse } from "../types";
import { Errors } from "../errors";

const DEFAULT_MODEL = "meta-llama/llama-3.3-70b-instruct:free";
const FALLBACK_MODEL = "qwen/qwen3-next-80b-a3b-instruct:free";
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const MAX_RETRIES = 3;
const TIMEOUT_MS = 30000;

// Custom error types
export class MalformedResponseError extends Error {
  code = "MALFORMED_RESPONSE";
  constructor(
    message: string,
    public rawContent: string,
  ) {
    super(message);
    this.name = "MalformedResponseError";
  }
}

export class EmptyResponseError extends Error {
  code = "EMPTY_RESPONSE";
  constructor(message = "LLM returned empty content") {
    super(message);
    this.name = "EmptyResponseError";
  }
}

export class AuthError extends Error {
  code = "AUTH_ERROR";
  constructor(message: string) {
    super(message);
    this.name = "AuthError";
  }
}

export class BadRequestError extends Error {
  code = "BAD_REQUEST_ERROR";
  constructor(message: string) {
    super(message);
    this.name = "BadRequestError";
  }
}

export class RateLimitExhausted extends Error {
  code = "RATE_LIMIT_EXHAUSTED";
  constructor(message = "Daily rate limit exhausted") {
    super(message);
    this.name = "RateLimitExhausted";
  }
}

export class TimeoutError extends Error {
  code = "TIMEOUT";
  constructor(message = "Request timed out") {
    super(message);
    this.name = "TimeoutError";
  }
}

// Validate request
function validateRequest(request: InferenceRequest): void {
  if (!request.messages || request.messages.length === 0) {
    throw Errors.badRequest("Messages list cannot be empty");
  }
}

// Build request headers
function buildHeaders(apiKey: string): Headers {
  const headers = new Headers();
  headers.set("Content-Type", "application/json");
  headers.set("Authorization", `Bearer ${apiKey}`);
  headers.set("HTTP-Referer", "https://insidher.app");
  headers.set("X-Title", "insidher");
  return headers;
}

// Build request body
function buildBody(
  model: string,
  request: InferenceRequest,
  includeTransforms: boolean,
): Record<string, unknown> {
  const body: Record<string, unknown> = {
    model,
    messages: request.messages.map((m) => ({
      role: m.role,
      content: m.content,
    })),
  };

  if (request.temperature !== undefined) {
    body.temperature = request.temperature;
  }

  if (request.maxTokens !== undefined) {
    body.max_tokens = request.maxTokens;
  }

  if (request.responseFormat) {
    body.response_format = {
      type: "json_schema",
      json_schema: {
        name: request.responseFormat.json_schema.name,
        strict: true,
        schema: request.responseFormat.json_schema.schema,
      },
    };
    if (includeTransforms) {
      body.transforms = ["response_format"];
    }
  }

  return body;
}

// Parse OpenRouter response into InferenceResponse
function parseResponse(
  data: Record<string, unknown>,
  isStructured: boolean,
): InferenceResponse {
  const choices = data.choices as Array<Record<string, unknown>> | undefined;
  const content = (choices?.[0]?.message as Record<string, unknown>)?.content as string;
  const usage = data.usage as Record<string, unknown> | undefined;
  const model = data.model as string;

  if (!content || content.trim() === "") {
    throw new EmptyResponseError();
  }

  let structuredContent: Record<string, unknown> | null = null;
  if (isStructured) {
    try {
      structuredContent = JSON.parse(content);
    } catch {
      throw new MalformedResponseError(
        "Failed to parse structured JSON content",
        content,
      );
    }
  }

  // Extract confidence from structured response if available
  let confidence = 0.5;
  if (structuredContent && typeof structuredContent.confidence === "number") {
    confidence = structuredContent.confidence;
  }
  // Clamp to [0.0, 1.0]
  confidence = Math.max(0.0, Math.min(1.0, confidence));

  const tokensUsed = (usage?.total_tokens as number) ?? 0;

  return {
    content,
    structuredContent,
    confidence,
    tokensUsed,
    model,
  };
}

// Sleep helper
function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Single fetch attempt with timeout
async function fetchWithTimeout(
  url: string,
  body: Record<string, unknown>,
  apiKey: string,
  timeoutMs: number = TIMEOUT_MS,
): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: buildHeaders(apiKey),
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    return response;
  } finally {
    clearTimeout(timeoutId);
  }
}

// Core completion function with retry logic
export async function completeInference(
  request: InferenceRequest,
  apiKey: string,
  options?: {
    url?: string;
    model?: string;
    fallbackModel?: string;
    dailyLimit?: number;
    currentDailyCount?: number;
  },
): Promise<InferenceResponse> {
  validateRequest(request);

  const url = options?.url ?? OPENROUTER_URL;
  const primaryModel = options?.model ?? request.model ?? DEFAULT_MODEL;
  const fallbackModel = options?.fallbackModel ?? FALLBACK_MODEL;

  // Check daily rate limit
  if (options?.dailyLimit !== undefined && options?.currentDailyCount !== undefined) {
    if (options.currentDailyCount >= options.dailyLimit) {
      throw new RateLimitExhausted();
    }
  }

  const startTime = Date.now();
  const isStructured = request.responseFormat !== null && request.responseFormat !== undefined;
  let lastError: Error | null = null;
  let usedFallback = false;

  // Try primary model
  let response = await tryModel(url, primaryModel, request, apiKey, isStructured);
  if (response !== null) {
    response.latencyMs = Date.now() - startTime;
    return response;
  }

  // Try fallback model (single attempt)
  usedFallback = true;
  response = await tryModel(url, fallbackModel, request, apiKey, isStructured, 1);
  if (response !== null) {
    response.latencyMs = Date.now() - startTime;
    return response;
  }

  void usedFallback;
  if (lastError) throw lastError;
  throw Errors.internal("All inference attempts failed");
}

// Try a model with exponential backoff retries
async function tryModel(
  url: string,
  model: string,
  request: InferenceRequest,
  apiKey: string,
  isStructured: boolean,
  maxRetriesOverride?: number,
): Promise<InferenceResponse | null> {
  const maxRetries = maxRetriesOverride ?? MAX_RETRIES;
  const totalAttempts = maxRetries + 1; // initial + retries
  const delays = [1000, 2000, 4000]; // exponential backoff in ms
  let healingAttempted = false;

  for (let attempt = 0; attempt < totalAttempts; attempt++) {
    const includeTransforms = isStructured && attempt > 0; // Add transforms on retry
    if (isStructured && attempt > 0 && !healingAttempted) {
      healingAttempted = true;
    }

    const body = buildBody(model, request, includeTransforms);

    let response: Response;
    try {
      response = await fetchWithTimeout(url, body, apiKey);
    } catch (err) {
      // Network error or timeout - retryable
      if (attempt < totalAttempts - 1) {
        const delay = delays[attempt] ?? delays[delays.length - 1];
        await sleep(delay);
        continue;
      }
      if (err instanceof Error && err.name === "AbortError") {
        throw new TimeoutError();
      }
      throw err;
    }

    if (response.ok) {
      const data = (await response.json()) as Record<string, unknown>;
      try {
        return parseResponse(data, isStructured);
      } catch (parseErr) {
        if (parseErr instanceof MalformedResponseError) {
          // Try healing with transforms on next retry
          if (attempt < totalAttempts - 1) {
            const delay = delays[attempt] ?? delays[delays.length - 1];
            await sleep(delay);
            continue;
          }
          throw parseErr;
        }
        throw parseErr;
      }
    }

    // Error responses
    if (response.status === 401 || response.status === 403) {
      // Non-retryable: auth error
      throw new AuthError(
        "OpenRouter authentication failed. Check API key configuration.",
      );
    }

    if (response.status === 400) {
      // Non-retryable: bad request
      const errBody = await response.text().catch(() => "Bad request");
      throw new BadRequestError(`OpenRouter bad request: ${errBody}`);
    }

    if (response.status === 429) {
      // Rate limited - respect Retry-After
      const retryAfter = response.headers.get("Retry-After");
      if (attempt < totalAttempts - 1) {
        const delay = retryAfter
          ? parseInt(retryAfter, 10) * 1000
          : (delays[attempt] ?? delays[delays.length - 1]);
        await sleep(delay);
        continue;
      }
      // Exhausted retries on this model
      return null;
    }

    // 5xx or other errors - retryable
    if (attempt < totalAttempts - 1) {
      const delay = delays[attempt] ?? delays[delays.length - 1];
      await sleep(delay);
      continue;
    }

    // Exhausted retries
    return null;
  }

  return null;
}

// Structured completion (convenience wrapper)
export async function completeStructured(
  request: InferenceRequest,
  schema: { name: string; schema: Record<string, unknown> },
  apiKey: string,
  options?: {
    url?: string;
    model?: string;
    fallbackModel?: string;
  },
): Promise<InferenceResponse> {
  const enhancedRequest: InferenceRequest = {
    ...request,
    responseFormat: {
      type: "json_schema",
      json_schema: {
        name: schema.name,
        strict: true,
        schema: schema.schema,
      },
    },
  };

  return completeInference(enhancedRequest, apiKey, options);
}

// Get default model from env
export function getDefaultModel(env?: { OPENROUTER_MODEL?: string }): string {
  return env?.OPENROUTER_MODEL ?? DEFAULT_MODEL;
}

// Get fallback model from env
export function getFallbackModel(env?: { OPENROUTER_FALLBACK_MODEL?: string }): string {
  return env?.OPENROUTER_FALLBACK_MODEL ?? FALLBACK_MODEL;
}
