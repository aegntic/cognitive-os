# Insidher — Product Scope, Goals & Agent Work Package Spec

**Status:** Working product definition (pre–full build)  
**Primary users:** Independent professionals, private practitioners, high-end consultants, creatives, and discreet service providers who need a polished front desk without hiring staff  
**Last updated:** snapshot after early Android MVP  
**Codename lineage:** Luxe Threshold → **Insidher**

---

## 1. Purpose (one sentence)

**Insidher** is a private, discreet, personalized professional receptionist and personal assistant: clients meet a customizable luxury front desk; the owner works with **Anita**, an internal sovereign PA who remembers, drafts, coordinates, and protects boundaries.

---

## 2. Problem

Professionals lose time and prestige to:

- Unfiltered DMs, emails, and booking chaos
- Generic chatbots that feel cheap or leaky
- Memory mix-ups across clients and contexts
- No calm “front office” that matches a luxury personal brand
- Admin that should feel private, controlled, and non-performative

Insidher exists so the **surface** is elegance and ease, while the **infrastructure** is explicit memory, auditability, and sovereignty over client data.

---

## 3. Goals

### 3.1 Product goals

1. **Front desk that feels human and discreet** — Clients message a personalized receptionist persona, not a random bot dump.
2. **Owner control** — User fully customizes the client-facing receptionist (voice, rules, availability, boundaries, brand).
3. **Anita as private staff** — Owner-facing agent named **Anita** handles internal work only; never confused with client channel.
4. **Memory with consent and Sovereignty** — Who remembers whom, overnight vault encryption, export/delete, no surprise cloud linguistics.
5. **Cross-platform by default** — One product story: iOS, Android, and browser, out of the box.
6. **Luxury craft** — Obsidian + marble + refined 3D depth — never generic SaaS blue.
7. **Sustainable business** — Subscription paywall + referral incentives without degrading the luxurious experience.
8. **Ship in parallel** — Scope broken so independent smaller agents can finish work packages without reinventing the product.

### 3.2 Non-goals (explicit)

- Mass-market freemium spam features
- Public social feeds / performative presence
- Replacing legal counsel, clinical therapy, or regulated medical systems (routing + boundary language only)
- Enterprise multi-tenant org HQ (future phase, not MVP)
- Auto-spamming every client without owner review/control

### 3.3 Success metrics (measurable)

| Metric                                       | Target (MVP → v1)                            |
| -------------------------------------------- | -------------------------------------------- |
| Time to first personalized receptionist live | < 15 minutes from install                    |
| Owner reply draft usable without edit        | ≥ 60% of inbound inquiries                   |
| Client/channel confusion rate                | Near zero (separate surfaces)                |
| Multi-platform feature parity (core)         | 100% for receptionist + Anita + vault basics |
| Paid conversion after trial                  | Tracked; referral lift measurable            |
| Data export on request                       | < 5 minutes user flow                        |
| Crash / failed send rate                     | Store-acceptable; formbacks never silent     |

---

## 4. Product identity

### 4.1 Brand voice

- Calm, elegant, ultra-premium, discreet
- Short beautiful sentences where the client sees them
- Precise and operational where the owner works with Anita
- No hype words, no millennial corporate “synergy”

### 4.2 Dual-agent model (non-negotiable)

| Surface                        | Name / role                                            | Audience          | Purpose                                        |
| ------------------------------ | ------------------------------------------------------ | ----------------- | ---------------------------------------------- |
| **Client-facing receptionist** | User-customizable (name, tone, style, avatar optional) | Clients / inbound | Intake, scheduling, status, elegant boundaries |
| **Owner-facing PA**            | **Anita** (fixed product name)                         | User only         | Memory, drafts, coordination, strategy, vault  |

Rules:

- Client messages and Anita chat are **separate surfaces forever**
- Drafts for clients can be _generated_ via Anita logic but _presented_ as “reply to client”
- Never blend threads or labels; UI copy must restate separation
- Audit log must show path: inbound → decision → drafted → sent / held

### 4.3 Philosophy

> Elegant flow on the surface. Explicit, auditable sovereignty underneath.

---

## 5. Core personas

1. **Owner** — Professional using Insidher as staff (primary payer).
2. **Client** — Person writing in; never sees Anita or vault internals.
3. **Anita** — Agent product personality for the owner.
4. **Custom receptionist** — Projection of owner’s brand toward clients.
5. **Referrer / referee** — Users in the referral incentive loop.

---

## 6. Primary user journeys

### 6.1 Owner — first run

1. Splash → onboarding (Who Remembers / Encrypted Vault / Sovereign Coordination)
2. Auth (sign up / login / recover)
3. Customize receptionist (name, tone, hours, languages, hard no’s)
4. Connect channels (email / SMS / web widget / botted chat later)
5. Meet Anita + understand separation
6. Trial → paywall when value is clear
7. Share referral if delighted

### 6.2 Client — contact

1. Writes via supported channel or web receptionist page
2. Gets a calm, on-brand first reply (auto or after owner rules)
3. Gets scheduling / follow-up without chaos
4. Never sees owner vault or Anita chat

### 6.3 Owner — daily with Anita

1. Opens Anita tab / web PA desk
2. Asks for drafts, memory recall, schedule pressure, risk flags
3. Approves/edits outbound if required by mode
4. Logs stay in vault; export possible

### 6.4 Billing & referral

1. Subscription offer after trial / feature gate
2. Upgrade → Stripe (or platform IAP on native)
3. Referral code / link earned
4. Referee rewards applied when conversion valid

---

## 7. Feature scope

### 7.1 MVP (must ship)

- [ ] Auth and session (email + password; secure reset)
- [ ] Encrypted vault for memory, inquiries, config
- [ ] Who Remembers: clients + inbound messages list
- [ ] One-tap generate reply-to-client text (copy / mark sent)
- [ ] Anita chat (owner only), separate UI and history
- [ ] Customizable receptionist profile (name, greeting, tone, availability windows, prohibited topics)
- [ ] Local decision engine (AnitaCore-style) with structured actions
- [ ] Audit log (immutable feel; encrypted storage in prod)
- [ ] Premium obsidian/marble theme (tokenized)
- [ ] Subscription paywall shell + status flags
- [ ] Referral code model (issue, redeem, balance pending)
- [ ] iOS + Android + browser **same core product surfaces**
- [ ] Privacy policy, terms, logout, data path documentation

### 7.2 v1 (shortly after MVP)

- [ ] True multi-channel intake (email, SMS, WhatsApp-class gateway, web widget)
- [ ] Real on-device and optional cloud LLM routing with owner toggle
- [ ] Schedule calendar sync (read + write, with consent)
- [ ] Template library + personal brand style cards
- [ ] Rich memory entities (people, preferences, boundaries, past notes)
- [ ] Draft approval modes: always auto / suggest only / after first reply
- [ ] Push notifications for high-signal inbound
- [ ] Full billing (Stripe + Apple/Google IAP where required)
- [ ] Referral dashboard with payout or credit rules
- [ ] Export/import vault pack
- [ ] Admin safety rail against prompt injection via client text

### 7.3 Later / optional

- [ ] Voice receptionist (phone)
- [ ] Multicaller routing for studios with assistants (still client vs Anita separation)
- [ ] Marketplace of receptionist “presets” (luxury vernacular packs)
- [ ] White-glove onboarding for high ARPU
- [ ] Compliance packs (GDPR DPA, SOC2 journey) for enterprise teaser

---

## 8. Platforms

**Requirement: iOS, Android, and browser-compatible out of the box.**

### 8.1 Strategy

Prefer a **shared product core + multi-shell** approach:

| Layer          | Approach                                                                             |
| -------------- | ------------------------------------------------------------------------------------ |
| Design system  | Shared tokens (obsidian, marble, amber-gold), components mapped per platform         |
| Business logic | Shared TypeScript / Kotlin multiplatform / Rust core (choose in eng architecture WP) |
| Mobile UI      | Flutter **or** RN **or** Compose Multiplatform — decision required (see decisions)   |
| Web            | Responsive SPA (Next/Vite) sharing API contracts with native                         |
| Backend        | Auth, billing, sync, referrals, channel webhooks                                     |
| Local edge     | Encrypted vault, offline draft queue, optional on-device model                       |

### 8.2 Platform-specific notes

- **iOS:** Store policies; use IAP for digital subscription; privacy nutrition labels accurate
- **Android:** Play Billing where required; register SMS/phone permissions carefully
- **Browser:** No full filesystem vault; use WebCrypto + optional user-held keys / server sealed vault
- **Parity bar:** Receptionist config, Anita chat, Who Remembers, paywall, referral entry must exist everywhere

---

## 9. Luxury visual system (3D obsidian + marble)

### 9.1 Material language

- **Obsidian:** deep blacks, etched charcoal, soft depth, no flat gray SaaS
- **Marble:** warm pearl veining, light catches, restrained gold inlay
- **Amber-gold:** primary accents, interactive affordances only (not wallpaper)
- **Glass / liquid depth:** layered cards, hydraulic presses on buttons, spring micro-motion
- **3D:** subtle lighting, specular response, parallax or mesh vignettes without gamer chrome

### 9.2 Implementation requirements

- Shared design tokens: `InsidherTokens` (or web CSS vars / design tokens JSON)
- Dark-dominant default; optional warmer “private lounge” variant later
- Motion budget: calm, spring-based, interruptible; never carnival
- Accessibility: contrast AA as minimum on text; reduce-motion respected
- Cross-platform asset pipeline for Lottie/Rive/shader equivalents

### 9.3 Non-visual luxury

- Typographic hierarchy and whitespace as status
- Copy restraint
- Error states that never scold; they guide softly

---

## 10. Privacy, security, sovereignty

### 10.1 Principals

- Owner data is **not product training data** without affirmative opt-in
- Client content is sensitive; treat as confidences
- Encryption at rest for vault (SQLCipher / platform Keystore / WebCrypto)
- Least privilege for cloud calls
- Clear modes: on-device only vs hybrid assist
- Right to export and delete

### 10.2 Threat classes to design for

- Prompt injection via client messages influencing Anita into unsafe actions
- Cross-client memory bleed
- Billing fraud on referral loops
- Token theft / session fixation
- Cloud provider retention software

### 10.3 Compliance checklist (MVP → v1)

- Privacy policy + terms live in app and web
- Consent for notifications and channel connect
- GDPR-style export/delete path
- Age gate if needed by jurisdictions later
- Logging policy: what is never logged in plaintext

---

## 11. Anita — owner PA behavior

### 11.1 Responsibilities

- Draft replies (for client channel, clearly labeled)
- Summarize inbound
- Recall client context from vault
- Surface scheduling conflicts
- Escalate “needs human judgment”
- Keep private counsel with owner about strategy and boundaries

### 11.2 Interface rules

- Conversation history owner-only
- Explicit “context client” picker when drafting about a person
- Structured actions: draft, schedule suggest, hold, escalate, silence
- Modes: rule engine first → optional LLM assist

### 11.3 Output quality bar

- Calm professional language for client drafts
- Direct, efficient language to owner
- Never invent appointments or send without mode permission

---

## 12. Client-facing receptionist (customizable)

### 12.1 Customizable fields (MVP)

- Display name
- Opening greeting
- Tone selection (e.g. Quiet luxury / Warm formal / Clinical precise / Creative soft)
- Working hours + timezone
- Hard boundaries / topics deferred
- Signature close
- Language(s)
- Escalation rules (when to involve owner)

### 12.2 Customizable fields (v1)

- Avatar / mark
- Channel-specific variants
- Multi-language auto detect
- Template replies
- Brand lexicon (words prefer/avoid)
- Booking depth (links only vs real calendar)

### 12.3 Channel behaviors

- First-response latency targets
- Quiet hours, auto-hold wording
- Rich link cards for booking when allowed

---

## 13. Monetization

### 13.1 Subscription paywall

Suggested tiers (names final with brand later):

| Tier             | Intent            | Unlocks (illustrative)                           |
| ---------------- | ----------------- | ------------------------------------------------ |
| **Trial**        | Feel the product  | Limited clients / drafts / no multi-channel      |
| **Private Desk** | Solo professional | Full receptionist + Anita + vault + 1–2 channels |
| **Atelier**      | Higher volume     | More channels, memory depth, voice later         |
| **Maison**       | White glove       | Priority support, custom brand pack              |

Rules:

- Paywall after value, not before first elegant experience if possible
- Feature gates must be honest; no dark patterns
- Restore purchases / manage subscription surfaces on all platforms
- Billing providers: Stripe (web + Android where allowed), App Store / Play Billing for native IAP when required

### 13.2 Referral incentives

- Unique referral code/link per owner
- **Referee:** e.g. extended trial or first-month credit
- **Referrer:** subscription credit or free months after paid conversion
- Fraud controls: device/email heuristics, self-referral block, cooldown
- Dashboard: invites sent, pending, converted, credit balance
- Legal: terms of referral in product ToS

---

## 14. Architecture outline (agent-ready)

```
┌────────────────────────────────────────────────────────────┐
│ Clients     Email / SMS / Web / IM                         │
└───────────────┬────────────────────────────────────────────┘
                ▼
┌────────────────────────────────────────────────────────────┐
│ Channel Gateway  (normalize inbound/outbound)              │
└───────────────┬────────────────────────────────────────────┘
                ▼
┌────────────────────────────────────────────────────────────┐
│ Receptionist Policy Engine  (brand, hours, boundaries)     │
└───────┬───────────────────────────────────────────┬────────┘
        ▼                                           ▼
  Draft / Auto-reply paths               Owner review queue
        │                                           │
        └──────────────┬────────────────────────────┘
                       ▼
┌────────────────────────────────────────────────────────────┐
│ Vault  (clients, messages, memory, logs) encrypted         │
└───────┬───────────────────────────┬────────────────────────┘
        ▼                           ▼
   Anita Owner UI             Sync / Billing / Referrals
```

### 14.1 Major subsystems

1. **Identity & Auth**
2. **Vault & Memory**
3. **Receptionist engine**
4. **Anita engine**
5. **Channels**
6. **Scheduling**
7. **Billing & paywall**
8. **Referrals**
9. **Design system / 3D theme**
10. **Web app shell**
11. **iOS shell**
12. **Android shell**
13. **Analytics (privacy-preserving)**
14. **Admin/ops (internal only)**
15. **Docs / legal / store listing**

---

## 15. Data model (conceptual)

- `User` — owner account, plan, referral code
- `ReceptionistProfile` — client-facing config
- `Client` — contact + sovereignty level + notes
- `Inquiry` / `Message` — inbound / outbound channel structured
- `AnitaThread` / `AnitaMessage` — owner-only chat
- `MemoryItem` — typed memories with provenance
- `ScheduleEvent`
- `AuditLogEntry`
- `Subscription` / `Entitlement`
- `ReferralInvite` / `ReferralCredit`
- `ChannelConnection`

(Exact schemas per platform in subsystem WPs.)

---

## 16. Work packages for independent smaller agents

Each package is **owner-independent**, has **inputs**, **outputs**, **definition of done**. Agents should not expand scope beyond DoD without product revision of this doc.

---

### WP-00 — Product governance & tracking

**Owner agent type:** PM / process  
**Inputs:** this doc  
**Outputs:** milestone board, issue labels, RAT decisions log  
**DoD:**

- [ ] Issues for all WPs
- [ ] Definition of MVP cut line locked
- [ ] Change log procedure for this document

---

### WP-01 — Engineering architecture decision

**Owner agent type:** tech lead  
**Decide:** Flutter vs RN vs KMP+native vs separate stacks  
**Outputs:** ADR, monorepo layout, shared package map  
**DoD:**

- [ ] Documented decision with tradeoffs
- [ ] Repo skeleton paths created
- [ ] CI bootstrap matrix (web, iOS, Android)

---

### WP-02 — Design system (obsidian + marble + amber)

**Owner agent type:** design eng  
**Outputs:** tokens JSON, dark theme, components (card, button, inputs, nav), 3D material guidelines  
**DoD:**

- [ ] Token file versioned
- [ ] Component gallery for web + native mapping
- [ ] Motion + reduce-motion
- [ ] Accessibility pass notes

---

### WP-03 — Auth & session

**Owner agent type:** backend + mobile  
**Outputs:** register/login/reset, session tokens, secure storage  
**DoD:**

- [ ] Works on all three platforms concepts
- [ ] Logout clears active identity
- [ ] Password safety basics (hashing server-side)

---

### WP-04 — Encrypted vault

**Owner agent type:** security / storage  
**Outputs:** store clients, inquiries, logs, config; key management  
**DoD:**

- [ ] Encryption at rest strategy per platform
- [ ] Unit tests for CRUD + wipe
- [ ] Export/delete stubs

---

### WP-05 — Who Remembers (client CRM lite)

**Owner agent type:** fullstack UI  
**Outputs:** client list, add client, message list, mark status  
**DoD:**

- [ ] CRUD basics
- [ ] Clear empty states
- [ ] Linked to vault

---

### WP-06 — Client auto-reply text generator

**Owner agent type:** agent/core  
**Outputs:** deterministic + optional LLM drafts labeled for **client**  
**DoD:**

- [ ] One-tap generate
- [ ] Copy / mark sent
- [ ] No setup beyond onboarding

---

### WP-07 — Anita chat (owner only)

**Owner agent type:** UI + agent  
**Outputs:** threaded chat UI separate from Who Remembers  
**DoD:**

- [ ] History persistence
- [ ] Context client attachment
- [ ] UI copy states separation clearly

---

### WP-08 — Receptionist customization studio

**Owner agent type:** product UI  
**Outputs:** editor for name/tone/hours/boundaries  
**DoD:**

- [ ] Live preview of client greeting
- [ ] Save to vault/profile
- [ ] Validation of required fields

---

### WP-09 — Receptionist policy engine

**Owner agent type:** backend/core  
**Outputs:** rule evaluation for hours, defer, escalate, templates  
**DoD:**

- [ ] Deterministic tests for rule matrix
- [ ] Structured action object shared with Anita

---

### WP-10 — Channel gateway (stubs → real)

**Owner agent type:** integrations  
**Outputs:** inbound normalizer, outbound adapter interface  
**DoD:**

- [ ] Pluggable channel interface
- [ ] Demo channel (fixture)
- [ ] Webhook signature verification pattern

---

### WP-11 — Scheduling

**Owner agent type:** calendar  
**Outputs:** availability, invite links, conflict flags to Anita  
**DoD:**

- [ ] Manual events MVP
- [ ] Calendar provider adapter interface
- [ ] Owner timezone correctness

---

### WP-12 — LLM provider layer

**Owner agent type:** ML platform  
**Outputs:** multi-provider, on-device optional, cost/latency controls  
**DoD:**

- [ ] Owner toggle on/off
- [ ] Safe system prompts separating receptionist vs Anita
- [ ] Fallback to rule engine when offline/fail

---

### WP-13 — Subscription paywall

**Owner agent type:** growth eng  
**Outputs:** tiers, store IAP + Stripe web, entitlement check  
**DoD:**

- [ ] Feature gates enforced server-side
- [ ] Restore purchase
- [ ] Trial → paid path tested

---

### WP-14 — Referral incentives

**Owner agent type:** growth eng  
**Outputs:** codes, attribution, credit ledger  
**DoD:**

- [ ] Anti-fraud baseline
- [ ] Owner dashboard view
- [ ] Terms link

---

### WP-15 — Web app

**Owner agent type:** frontend  
**Outputs:** browser SPA with Who Remembers, Anita, settings, paywall  
**DoD:**

- [ ] Responsive luxury layout
- [ ] Auth gate
- [ ] Shared API contracts consumed

---

### WP-16 — Android app

**Owner agent type:** mobile  
**Outputs:** current package path continued/port  
**DoD:**

- [ ] Store-ready bundle path
- [ ] Parity with MVP feature list
- [ ] Tone/theme integrity

---

### WP-17 — iOS app

**Owner agent type:** mobile  
**Outputs:** iOS client parity  
**DoD:**

- [ ] Keychain + attestation path as needed
- [ ] IAP wired
- [ ] Theme fidelity

---

### WP-18 — Sync & multi-device

**Owner agent type:** backend  
**Outputs:** incremental sync of vault-safe cloud replica (optional hybrid)  
**DoD:**

- [ ] Conflict strategy
- [ ] Device revoke
- [ ] Offline queue

---

### WP-19 — Analytics (privacy-first)

**Owner agent type:** data  
**Outputs:** funnel events without client message content by default  
**DoD:**

- [ ] Event schema
- [ ] Opt-out
- [ ] No raw client message capture unless togglable and explicit

---

### WP-20 — Security & abuse

**Owner agent type:** security  
**Outputs:** threat model, rate limits, injection mitigations  
**DoD:**

- [ ] Threat model doc
- [ ] Client→Anita isolation tests
- [ ] Secrets never in repo

---

### WP-21 — Legal, privacy, store listing

**Owner agent type:** ops/content  
**Outputs:** Privacy, Terms, store screenshots metadata, copy  
**DoD:**

- [ ] In-app links
- [ ] Accurate permissions text
- [ ] Referral language reviewed

---

### WP-22 — QA matrix & release

**Owner agent type:** QA  
**Outputs:** cross-platform test plan, regression suite  
**DoD:**

- [ ] MVP checklist signed
- [ ] Paywall + referral edge cases
- [ ] Accessibility smoke

---

### WP-23 — Demo content & guided tour

**Owner agent type:** design content  
**Outputs:** synthetic clients/inquiries, Anita intro  
**DoD:**

- [ ] First-run feels complete without real channels
- [ ] Easy clear/reset demo data

---

### WP-24 — Ops & support internals

**Owner agent type:** platform  
**Outputs:** admin dashboards for cancellations/referral disputes (minimal)  
**DoD:**

- [ ] Internal only
- [ ] Audit log of support actions

---

## 17. Cross-agent contracts

All agents must obey:

1. **Anita ≠ client receptionist** — never merge threads or nav labels.
2. **Client drafts labeled as client drafts.**
3. **No secrets in git** — keys, signing, `.env` local only.
4. **Theme tokens** — don’t invent random palettes; extend tokens.
5. **MVP first** — no enterprise quiet-feature burrow.
6. **Definition of Done includes** builds + light tests + screenshot/evidence path when UI.
7. **API contracts versioned** — OpenAPI or shared types module.
8. **Document deviations** in `docs/decisions/` ADRs.

Shared artifacts location (proposed):

```
02_Showcase/THRESHOLD-INC/luxe-agent/
  docs/
    INSIDHER_PRODUCT_SCOPE.md   ← this file
    decisions/
    api/
    design/
  app/                          ← current Android MVP
  (future) packages/ or apps/
```

---

## 18. Dependencies between WPs (simplified)

```
WP-01 Architecture ──► WP-02 Design system
        │                     │
        ├──► WP-03 Auth ──► WP-04 Vault ──► WP-05/06/07
        │                     │
        ├──► WP-08 Studio ──► WP-09 Policy
        ├──► WP-10 Channels
        ├──► WP-11 Schedule
        ├──► WP-12 LLM
        ├──► WP-13 Paywall ──► WP-14 Referrals
        └──► WP-15/16/17 shells
                    │
                    └──► WP-18 Sync, WP-19 Analytics, WP-20 Security
                                  └──► WP-21 Legal, WP-22 QA, WP-23 Demo
```

Parallelism tip: Design (02), Architecture (01), and Legal drafts (21) can start same day. After auth/vault, Who Remembers / Anita / Studio fan out.

---

## 19. Acceptance criteria for whole product MVP

MVP is free to call “shipping” when:

1. Owner can create account (or demo identity), customize receptionist, receive synthetic client message, generate copy-ready reply, and chat with Anita without channel confusion.
2. Same three surfaces work on **browser + at least one mobile store path** (second mobile may trail by one sprint if risk). Ideally all three shells at MVP.
3. Subscription gate can block a premium surface and restore after mock or real purchase in staging.
4. Referral can generate a code and record an attribution event (full payout can be soft).
5. Theme reads as obsidian/marble luxury, not bootstrap dark mode.
6. Encrypted vault holds clients/memories/logs with logout wipe of ephemeral session.
7. Privacy + terms reachable; no accidental PII in analytics.

---

## 20. Risks & mitigations

| Risk                            | Mitigation                              |
| ------------------------------- | --------------------------------------- |
| Platform sprawl delays craft    | Shared core + hard parity cut line      |
| LLM costs / latency             | Rule-first + opt-in model; caching      |
| Confusion Anita vs receptionist | Hard UI separation; copy review         |
| App store billing friction      | Correct IAP vs Stripe role split        |
| Privacy incidents               | Local vault priority; threat model      |
| Scope creep into full CRM       | Who Remembers stays intentionally light |
| Referral fraud                  | Credits only post paid conversion       |

---

## 21. Open product decisions (need owner call later)

1. **Shared mobile stack** (Flutter vs CMP vs RN vs dual native)
2. **Which channels in first public beta**
3. **Exact tier names/prices**
4. **Referral credit size and payout currency vs subscription credit only**
5. **Mandatory cloud sync vs local-first with optional backup**
6. **Anita voice** prototype or pure text v1
7. **Name string of client-facing receptionist default** when user doesn’t customize

Until decided, engineering proceeds with interfaces and sensible defaults documented in ADRs.

---

## 22. Current codebase reality (honesty)

There is an **early Android/Kotlin Compose MVP** under this tree:

- Auth / onboarding shells
- Who Remembers + auto-reply drafting
- Separate Anita chat surface (partial)
- EncryptedVault patterns
- Obsidian/amber-gold styling beginnings

It is **not** yet multi-platform, full paywall live, referral live, or full receptionist studio.  
This document defines the **target product** and the **task decomposition** so smaller agents can complete the remainder without inventing purpose.

---

## 23. How to use this doc with smaller agents

When spawning an agent:

1. Paste **§1 Purpose**, **§4 dual-agent rules**, and the single **WP-XX** block.
2. Attach relevant contracts (tokens, API stubs).
3. Require DoD artifacts in PR: code + tests/proof + note if product doc needs update.
4. Forbid cross-WP silence changes without updating this file.

Example kickoff prompt fragment:

> Implement WP-07 only. Respect dual-agent separation. Anita is owner-only. Do not mix with Who Remembers messaging. Theme tokens only. Return DoD checklist.

---

## 24. Glossary

| Term              | Meaning                                          |
| ----------------- | ------------------------------------------------ |
| **Insidher**      | Product name: private receptionist + PA system   |
| **Anita**         | Owner-facing sovereign PA                        |
| **Receptionist**  | Client-facing customizable front desk persona    |
| **Who Remembers** | Client/memory surface for inbound relationships  |
| **Vault**         | Encrypted storage of sensitive product data      |
| **Sovereignty**   | Owner control over memory, channels, and handoff |
| **Draft**         | Suggested reply text, not necessarily sent       |

---

## 25. One-page pitch (copy bank)

### Tagline options

- Inside, someone remembers.
- A private front desk. A personal Anita.
- Discreet reception. Sovereign memory.

### Elevator

Insidher is the private receptionist and personal assistant for professionals who need discretion. Clients meet a customizable luxury front desk; you work with Anita — who holds context, drafts with care, and never confuses the two rooms of the house.

---

## Document control

| Field     | Value                                                                    |
| --------- | ------------------------------------------------------------------------ |
| Location  | `docs/INSIDHER_PRODUCT_SCOPE.md`                                         |
| Audience  | Product, engineering agents, design, growth                              |
| Authority | Product owner must approve goal changes in §3 and dual-agent rules in §4 |
| Companion | ADR folder to be created under `docs/decisions/`                         |

---

_End of Insidher comprehensive product scope. Use WP packages as the atomic unit of agent work._
