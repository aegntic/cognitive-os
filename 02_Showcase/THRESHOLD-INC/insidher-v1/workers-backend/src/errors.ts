import type { Context } from "hono";
import type { ApiResponse } from "./types";

// Error class with HTTP status code
export class ApiError extends Error {
  constructor(
    public statusCode: number,
    public code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// Cast a numeric status code to Hono's ContentfulStatusCode
export function asStatusCode(code: number): 200 | 201 | 202 | 400 | 401 | 403 | 404 | 405 | 409 | 429 | 500 | 502 | 503 {
  return code as 200 | 201 | 202 | 400 | 401 | 403 | 404 | 405 | 409 | 429 | 500 | 502 | 503;
}

export const Errors = {
  badRequest: (msg: string, code = "BAD_REQUEST") => new ApiError(400, code, msg),
  unauthorized: (msg = "Unauthorized") => new ApiError(401, "UNAUTHORIZED", msg),
  forbidden: (msg = "Forbidden") => new ApiError(403, "FORBIDDEN", msg),
  notFound: (msg = "Not found") => new ApiError(404, "NOT_FOUND", msg),
  methodNotAllowed: (allowed: string[]) =>
    new ApiError(405, "METHOD_NOT_ALLOWED", `Allowed: ${allowed.join(", ")}`),
  conflict: (msg: string) => new ApiError(409, "CONFLICT", msg),
  rateLimited: (retryAfter: number) =>
    new ApiError(429, "RATE_LIMITED", `Too many requests. Retry after ${retryAfter}s.`),
  internal: (msg = "Internal server error") => new ApiError(500, "INTERNAL_ERROR", msg),
  badGateway: (msg = "Bad gateway") => new ApiError(502, "BAD_GATEWAY", msg),
  unavailable: (msg = "Service unavailable") => new ApiError(503, "UNAVAILABLE", msg),
};

// Create error response envelope
export function errorResponse(
  _statusCode: number,
  code: string,
  message: string,
): ApiResponse<never> {
  return { success: false, error: { code, message } };
}

// Hono error handler middleware
export function errorHandler(err: Error, c: Context): Response {
  if (err instanceof ApiError) {
    const body = errorResponse(err.statusCode, err.code, err.message);
    const headers: Record<string, string> = {};
    if (err.statusCode === 405) {
      // Extract allowed methods from message
      const match = err.message.match(/Allowed: (.+)/);
      if (match) headers["Allow"] = match[1];
    }
    if (err.statusCode === 429) {
      const match = err.message.match(/Retry after (\d+)s/);
      if (match) headers["Retry-After"] = match[1];
    }
    return c.json(body, asStatusCode(err.statusCode), headers);
  }

  // Never expose internal error details
  console.error("Unhandled error:", err);
  const body = errorResponse(500, "INTERNAL_ERROR", "Internal server error");
  return c.json(body, 500);
}
