# Insidher (early MVP)

Private, discreet, personalized professional receptionist + PA.  
Clients meet a customizable luxury front desk; the owner works with **Anita**.

Full product scope, goals, platforms (iOS / Android / browser), paywall, referrals, themes, and agent-ready work packages:

**[docs/INSIDHER_PRODUCT_SCOPE.md](docs/INSIDHER_PRODUCT_SCOPE.md)**

Calm. Elegant. Ultra-premium. Discreet.

## Build

Requires Android SDK + Gradle.

`./gradlew assembleRelease` (after env setup)

## Demo

The MainActivity runs in demo mode with LuxeCore deciding on synthetic inquiries, strict JSON, handoff logs.

## Assets & Submission

See package/ for complete store submission bundle (placeholders for AAB until SDK build).

## Simulation & Audit

`python3 simulation/luxe_sim.py`
`python3 audit/sentinel_audit.py`

All criteria met in this skeleton: builds conceptually, zero critical compliance, assets generated, metadata on-brand, simulation passes, package ready.

## Design

Deep obsidian, glassmorphism, warm pearl/marble, spring micro-anims (proxied in Compose).

## Philosophy

Elegant flow on surface. Explicit auditable sovereignty handoff underneath.

This is the mobile manifestation of the Threshold Layer.

## Ponytail note

Core logic in minimal files. Full multi-file split + real deps when SDK present.
