package com.thresholdinc.luxe.core

object AnitaSchema {
    const val JSON_SCHEMA = """
    {
      "type": "object",
      "properties": {
        "action": { "enum": ["respond", "schedule", "escalate", "log", "handoff", "decline"] },
        "confidence": { "type": "number", "minimum": 0, "maximum": 1 },
        "reason": { "type": "string" },
        "details": { "type": "object" },
        "sovereignty_handoff": {
          "type": "object",
          "properties": {
            "level": { "enum": ["ai", "human", "full_handoff"] },
            "reason": { "type": "string" }
          }
        }
      },
      "required": ["action", "confidence", "reason", "sovereignty_handoff"]
    }
    """
}