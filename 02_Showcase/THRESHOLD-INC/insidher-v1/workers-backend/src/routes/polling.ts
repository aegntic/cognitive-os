import { Hono } from "hono";
import type { Env, ApiResponse } from "../types";
import {
  getPendingOutboundSms,
  markOutboundSmsDelivered,
} from "../db/outbound-sms";

const polling = new Hono<{ Bindings: Env }>();

// GET /api/devices/:deviceId/outbound - Poll for pending outbound SMS
polling.get("/:deviceId/outbound", async (c) => {
  const deviceId = c.req.param("deviceId");
  const pendingSms = await getPendingOutboundSms(c.env.DB, deviceId);

  const response: ApiResponse<typeof pendingSms> = {
    success: true,
    data: pendingSms,
  };

  return c.json(response, 200);
});

// POST /api/devices/:deviceId/outbound/:smsId/delivered - Confirm SMS delivery
polling.post("/:deviceId/outbound/:smsId/delivered", async (c) => {
  const smsId = c.req.param("smsId");
  await markOutboundSmsDelivered(c.env.DB, smsId);

  const response: ApiResponse<{ status: string }> = {
    success: true,
    data: { status: "delivered" },
  };

  return c.json(response, 200);
});

export default polling;
