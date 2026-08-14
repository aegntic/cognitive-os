# Insidher — Path to Real Users Without Friction or Lawsuits

**Status:** Strategic decision document (not legal advice)  
**Scope:** Product, distribution, compliance, age verification, technical gates  
**Audience:** Founder / operator deciding how to ship Insidher  
**Related stack:** Android app + Cloudflare Workers backend + SMS + optional LLM (OpenRouter)  
**Date:** 2026-07-16

---

## 1. Executive summary

Insidher is a **device-bound SMS conversation agent** with deposit-gated human approval. That stack can become a real product people pay for—or a lawsuit and Play rejection machine—depending almost entirely on **who it is for** and **what you claim it does**.

There are only two durable paths:

| Path                                    | Positioning                                                          | Distribution                               | Adult / escort ops                                                 |
| --------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------ |
| **A — Professional productivity**       | Appointment / client SMS assistant for legitimate service businesses | Google Play + web waitlist                 | **Out of product and listing entirely**                            |
| **B — Restricted / adult-adjacent ops** | Private tooling for 18+ operators                                    | **Not** Google Play; private or enterprise | Only with aggressive age verification, contracts, and legal review |

**Recommendation:** Ship **Path A** as the public product. If Path B demand exists, treat it as a **separate product, package, and legal entity**—never a toggle inside the Play build.

**Non-negotiables for either path:**

1. **Age verification (must)** before any SMS automation, deposit flow, or persona that can message third parties.
2. **Honest AI disclosure** to the _operator_ (app user); no marketing that claims “clients can never detect AI.”
3. **Human remains liable** for outbound SMS; product is assistive, not a black-box impersonator.
4. **Payments and deposits** go through a licensed processor with clear merchant-of-record and refund rules.
5. **Privacy policy + Data safety** before any public install.

Without those five, “get it into users’ hands without friction” is false friction: you trade install friction for ban and lawsuit friction later.

---

## 2. What “real” means for this product

### 2.1 Job to be done (path-independent)

- Owner installs on a phone that can receive/send SMS.
- Owner defines a **persona / business voice**.
- Inbound client SMS is processed safely.
- Outbound replies are drafted (AI and/or templates) and sent with timing that feels human.
- Money-related steps (deposit / hold) require **owner confirmation** before final commitment messaging.
- Audit trail exists for disputes.

### 2.2 What must never be the product’s job

- Facilitating commercial sexual services as a core use case on a consumer store.
- Hiding that the operator is using AI from the _operator_ (or from regulators).
- Processing deposits without KYC/AML-aware payment rails where required.
- Claiming zero legal risk because “it’s just SMS.”

### 2.3 Current technical reality (baseline)

Already built or partially built in `insidher-v1/`:

- Gradle multi-module: `:contracts`, `:core`, `:android-app`, `workers-backend`
- Thread state machine, safety codes, deposit/human gate invariants
- ECDSA device auth, D1, queues, SMS webhook + outbound poll
- Android onboarding, walkthrough, SMS receive/send permissions

**Gaps to “real users”:** store compliance, age verification, payments truth, AI disclosure UX, SMS policy story, ops docs, and clean professional positioning end-to-end.

---

## 3. Decision tree: which business you are building

```
                    ┌─────────────────────────┐
                    │ Who is the paying user? │
                    └───────────┬─────────────┘
                                │
           ┌────────────────────┼────────────────────┐
           ▼                                         ▼
  Legitimate service business              Adult / intimacy / escort ops
  (salon, consultant, coach, clinic)       (or “discreet companion” branding)
           │                                         │
           ▼                                         ▼
     PATH A — Play + web                      PATH B — Private / enterprise
     Professional productivity                Restricted distribution
           │                                         │
           ▼                                         ▼
  Age gate still required                     Age gate + contracts + counsel
  (min 18 for account / SMS auto)             (strict 18+, maybe 21 by market)
```

**Mixing paths in one APK is the highest-risk option.** Reviewers, complainants, and courts look at capability + marketing + residual strings, not your private intent.

---

## 4. Lawsuit and enforcement risk map

Risks below are operational, not a substitute for counsel in AU/US/EU (and any market you enable SMS in).

### 4.1 High severity

| Risk                                           | Trigger                                                  | Mitigation                                                                             |
| ---------------------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| **Facilitating illegal or regulated services** | Product used to run prohibited bookings                  | Path A only on public stores; ToS ban; monitoring; kill-switch                         |
| **Impersonation / fraud claims**               | Client believes they text a human; harm or money loss    | Operator disclosure; optional client-facing “assisted reply” mode; human gate on money |
| **Payment disputes / chargebacks**             | Deposits without clear goods/services, refunds, receipts | Stripe (or similar) + written booking terms + audit log                                |
| **Privacy / TCPA-like / spam**                 | Automated SMS without consent of the _client_            | Client initiated SMS first; no cold outbound campaigns in v1                           |
| **Platform ban (Play / Apple / carriers)**     | Restricted permissions, adult framing, policy mismatch   | Honest listing; SMS justification; Path A packaging                                    |

### 4.2 Medium severity

| Risk                                          | Trigger                               | Mitigation                                                             |
| --------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------- |
| **AI deception claims**                       | “Undetectable AI” marketing           | Remove; sell “faster, consistent replies you approve”                  |
| **Safety failures (minors, harm)**            | No age gate; weak safety              | Age verification **must**; keep fail-closed safety worker              |
| **Data breach**                               | SMS + phone numbers on device/backend | Encryption at rest, key hygiene, retention limits, DPA with LLM vendor |
| **Employment / contractor misclassification** | N/A unless you staff humans           | Keep product pure software                                             |

### 4.3 “Adult admin” specific exposure

Even if legal in a jurisdiction for an individual operator:

- **Play/App Store** will treat escort-adjacent automation as restricted.
- **Payment processors** can freeze funds for high-risk MCC / adult patterns.
- **Carriers** can block SMS traffic that looks like spam or commercial adult advertising.
- **Plaintiff theories** include facilitation, negligence (if a minor is messaged), and consumer deception.

**Age verification does not legalize a prohibited service model.** It only reduces underage contact risk.

---

## 5. Age verification (must-have design)

Age verification is **mandatory before**:

- Completing account / device registration for production
- Enabling outbound SMS automation
- Enabling deposit / human-review money flows
- Any persona that can message third parties

### 5.1 Who is verified

| Party                         | Required?                             | Notes                                                                                          |
| ----------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Operator (app user)**       | **Yes — hard gate**                   | Device owner who runs the agent                                                                |
| **Client (SMS counterparty)** | Soft gate v1; hard where law requires | Prefer client-initiated SMS; block patterns indicating minors (already partly in SafetyWorker) |

### 5.2 Recommended operator age verification ladder

Use a **progressive gate** so friction stays low for legitimate adults, while keeping a hard stop for underage.

| Level                          | When                        | Method                                         | Friction          | Assurance                            |
| ------------------------------ | --------------------------- | ---------------------------------------------- | ----------------- | ------------------------------------ |
| **L0 — Declarative**           | Dev / internal only         | “I am 18+” checkbox                            | Lowest            | Useless alone for production         |
| **L1 — Soft signal**           | Early waitlist              | DOB + jurisdiction + credit card preauth $0–1  | Low               | Weak; still better than L0           |
| **L2 — Document / vendor KYC** | **Production default**      | Persona, Stripe Identity, Onfido, Veriff, etc. | Medium (one-time) | Strong enough for most consumer apps |
| **L3 — Biometric + liveness**  | High-risk markets or Path B | Face match to ID                               | Higher            | Highest                              |

**Production must ship at least L2** for the operator account. L0/L1 are not “age verification” for lawsuit defense.

### 5.3 Product flow (operator)

```
Install
  → Walkthrough (honest product story)
  → Age verification (L2)  ← hard block
  → Profile (name + photo)
  → SMS permissions (with explanation)
  → Device registration + backend
  → Live
```

### 5.4 Implementation principles

- **Server-authoritative:** verification status lives on Workers/D1 (`age_verified_at`, `age_vendor`, `jurisdiction`). Client cannot self-certify production unlock.
- **One-time, reusable:** do not re-scan every launch; re-check on device transfer / new device key.
- **No SMS automation until verified.** Draft-only mode optional; send disabled.
- **Audit:** store vendor decision ID, not full ID images, unless counsel requires retention.
- **Min age:** 18 default; allow config to 21 for specific jurisdictions without forking the app.

### 5.5 Client-side underage risk

Even with operator 18+:

- Keep **MINOR_SAFETY_RISK** and related escalations fail-closed.
- Never optimize for “bypass safety to convert deposit.”
- If a client claims to be under 18, auto-escalate and stop automated replies.

---

## 6. Distribution strategy (low friction that stays legal)

### 6.1 Path A — Public consumer (recommended)

**Goal:** Install from Google Play with minimal steps; grow via waitlist and word of mouth.

1. **Waitlist web** (already adjacent in THRESHOLD-INC work) — email/phone, jurisdiction, 18+ attestation → invite code.
2. **Google Play closed testing** → open testing → production.
3. **Invite codes** rate-limit abuse and keep early users supportable.
4. **No APK sideloads** as the primary funnel (malware reputation + update chaos).

**Play-critical work (before production listing):**

- Rewrite all copy, defaults, demos, and test fixtures to **professional services** language.
- **Data safety form** + hosted privacy policy + terms.
- **SMS permission declaration** with a credible core-use story (see §7).
- **Target audience** 18+ if any residual risk; otherwise general with account 18+.
- Remove “undetectable AI” claims; sell productivity.
- Cleartext HTTP only in debug builds; release = HTTPS only.

### 6.2 Path B — Restricted (only if you insist on adult ops)

- **Do not use Google Play** for that binary.
- Private distribution (managed Google Play / enterprise / direct with signed APK + update server).
- **L2/L3 age verification + signed operator agreement** before activation.
- Payment processor that accepts the MCC; expect higher fees and freezes.
- Separate brand, package id, and backend tenant from Path A.

### 6.3 Friction budget (what users will tolerate)

| Step          | Max acceptable friction | Notes                                       |
| ------------- | ----------------------- | ------------------------------------------- |
| Install       | 1 tap                   | Store or invite link                        |
| Age verify    | 2–4 minutes, once       | Vendor UX; cache result                     |
| Profile       | 30 seconds              | Name + optional photo                       |
| SMS perms     | 1 system dialog         | Explainer screen first                      |
| First success | &lt; 10 minutes         | Receive a real SMS, see draft, approve send |

Anything above that needs to be optional or progressive.

---

## 7. SMS, AI, and payments — the three hard product laws

### 7.1 SMS permissions (Android / Play)

Current app uses `RECEIVE_SMS`, `SEND_SMS`, `READ_SMS`. Google treats these as **restricted**.

**Options (pick one primary story):**

| Option                                           | Description                                       | Friction        | Play odds                                              |
| ------------------------------------------------ | ------------------------------------------------- | --------------- | ------------------------------------------------------ |
| **A1 — Default SMS handler**                     | App becomes the user’s SMS app                    | High UX cost    | Better policy fit, product changes                     |
| **A2 — Limited SMS access + strong declaration** | Core feature is business SMS automation on-device | Medium          | Possible with careful declaration; still high scrutiny |
| **A3 — No SMS perms; use companion**             | Cloud number (Twilio etc.) or share-sheet send    | Lower Play risk | Product pivot; less “this phone number” magic          |

**Recommendation for Path A:** Prefer **A3 for v1 public** if Play review blocks A2; keep A2 as “power mode” after approval. Path B private builds can keep A2 without Play.

Never ship SMS automation without:

- Operator age verification
- Explicit opt-in: “I authorize this app to read/send SMS on my behalf”
- Easy kill switch and per-thread pause

### 7.2 AI disclosure and liability

| Audience      | Disclosure                                                                                          |
| ------------- | --------------------------------------------------------------------------------------------------- |
| **Operator**  | Clear in-app: AI drafts replies; you own the content and legal consequences                         |
| **Client**    | Optional v1; recommended for Path A: “You’ll get a reply shortly” without deepfake intimacy framing |
| **Marketing** | Forbidden: “clients will never know,” “undetectable,” “fully autonomous closer”                     |

Human gate on deposit/confirmation is your best liability shield—**do not remove it** to reduce friction.

### 7.3 Deposits and money

- Use a real PSP (e.g. Stripe) with correct business verification.
- Deposit = booking hold with written terms (amount, refund, no-show).
- Confirmation SMS only after **persisted owner APPROVE** (already an invariant—keep it).
- Never store full card data on device or Workers.

---

## 8. Architecture changes for a shippable product

### 8.1 Compliance layer (new)

Add server-side entities:

- `operators` / `accounts`: jurisdiction, age status, KYC vendor refs
- `devices`: link ECDSA device keys to verified operator
- Feature flags: `sms_send_enabled`, `deposits_enabled` only if `age_verified`
- Policy pack: `professional` vs `restricted` (if you ever dual-stack, separate tenants)

### 8.2 Safety layer (keep and harden)

- Keep fail-closed safety and minor/harm escalations.
- Add rate limits per client phone and per operator.
- Add “freeze outbound” kill switch for support.

### 8.3 Observability

- Audit logs already started—extend for age events, permission grants, send approvals.
- Support tooling: lookup thread by phone, export audit for disputes.

### 8.4 Professional packaging checklist

- Remove residual adult vocabulary from UI, demos, prompts, fixtures, confirm SMS templates.
- Persona defaults: professional services language only.
- Walkthrough: appointment/client messaging productivity, not “discreet companion.”
- Release builds: TLS only to production API.

---

## 9. Legal and policy artifacts (ship blockers)

Before public users:

1. **Terms of Service** — operator is controller of SMS content; prohibited uses list; indemnity.
2. **Privacy Policy** — SMS content, phone numbers, device keys, LLM subprocessors, retention.
3. **Data Processing / subprocessor list** — Cloudflare, OpenRouter (or local LLM), age vendor, PSP.
4. **Acceptable Use Policy** — ban illegal services, spam, targeting minors.
5. **Booking / deposit terms template** — operator can show clients.
6. **Google Play Data safety + declarations** — accurate, not aspirational.
7. **Age verification records policy** — what you store, how long, deletion.

**Counsel review** for AU (base of product language/AUD) plus any country you enable SMS numbers in.

---

## 10. Go-to-market: low-friction user acquisition

### 10.1 Funnel

```
Landing / waitlist
  → 18+ + jurisdiction
  → Invite
  → Play install (or private link for beta)
  → Age verify (L2)
  → Profile
  → SMS explain + permissions
  → First inbound SMS success metric
  → Paid plan (optional)
```

### 10.2 Ideal first users (Path A)

- Solo consultants, coaches, salons, mobile services, small clinics
- Already use personal phone for bookings
- Hate typing the same SMS 40 times a day
- Will accept “AI draft + my approval on money”

### 10.3 Metrics that mean “real”

| Metric                                 | Healthy early signal                           |
| -------------------------------------- | ---------------------------------------------- |
| Time to first successful inbound→draft | &lt; 15 min after install                      |
| Age verify completion rate             | &gt; 70% of installs that start KYC            |
| Human approve latency on deposits      | Median &lt; 2 hours (shows humans are in loop) |
| Chargeback / complaint rate            | Near zero in beta                              |
| Play policy strikes                    | Zero                                           |

### 10.4 Pricing (sketch)

- Free: N threads / month, no deposits
- Pro: deposits + higher volume
- Do not price “undetectable” or “adult mode”

---

## 11. Phased roadmap (highest leverage order)

### Phase 0 — Positioning freeze (1 week)

- Choose Path A or B.
- Freeze store story and prohibited-use list.
- Stop all “undetectable AI / discreet companion” language.

### Phase 1 — Compliance MVP (2–4 weeks)

- Operator age verification L2 (vendor) + server flags.
- ToS / Privacy / AUP live URLs.
- Professional copy pass entire app + backend templates.
- HTTPS production API; debug-only cleartext.
- Human gate remains mandatory for confirmations.

### Phase 2 — Closed beta (2–6 weeks)

- 20–50 invited operators, one jurisdiction.
- Play closed testing **or** private APK for Path B.
- Support channel; freeze outbound button.
- Measure first-SMS success and KYC completion.

### Phase 3 — Public Path A

- Play open testing → production.
- SMS strategy finalized (A2 or A3).
- Stripe deposits live with terms.
- Scale invites; add multi-device carefully.

### Phase 4 — Optional Path B (separate)

- Only after counsel + payments + private distribution.
- Separate package name, backend tenant, branding.
- L2/L3 age + signed operator agreements.

---

## 12. Explicit “do not do” list

1. Do not ship adult-admin marketing on a Play-listed APK.
2. Do not rely on a checkbox as age verification.
3. Do not disable human approval on deposits to “reduce friction.”
4. Do not market AI as undetectable to clients.
5. Do not cold-text strangers; client-initiated only in v1.
6. Do not store LLM vendor API keys in the APK (already correct—keep it).
7. Do not invent legal compliance in READMEs; link real policies and counsel.
8. Do not mix Path A and Path B in one binary with a hidden flag.

---

## 13. Recommended default strategy (one paragraph)

<!-- markdownlint-disable-next-line MD036 -->

**Make Insidher a professional, 18+ verified SMS booking assistant for legitimate service businesses; verify operator age with a real KYC vendor before any send; keep human approval on money; put AI honesty in the product, not in dark patterns; ship Path A on Google Play with clean copy and a defensible SMS story (or cloud number if Play blocks SMS perms); if adult ops remain a business, isolate them as Path B with private distribution and stronger contracts—never as a Play “mode.”**

That is the highest-probability path to real users **without** trading short-term install friction for long-term bans and lawsuits.

---

## 14. Immediate next engineering tickets (when you execute)

1. `operators` table + `age_verified` gate on send/deposit APIs.
2. Integrate age vendor (Persona / Stripe Identity / Veriff)—server webhook.
3. Professional copy pass (UI, walkthrough, LLM system prompts, confirm/reject SMS).
4. Production HTTPS API + release Network Security Config.
5. Privacy Policy + Terms + Play Data safety draft.
6. Decide SMS strategy A2 vs A3; implement declaration or cloud number.
7. Closed beta invite codes + kill switch.

---

## 15. Document control

| Field          | Value                                                     |
| -------------- | --------------------------------------------------------- |
| Owner          | THRESHOLD-INC / Insidher                                  |
| Classification | Internal strategy                                         |
| Not            | Legal advice, Play guarantee, or payments guarantee       |
| Review cadence | Before each distribution milestone (beta, Play, payments) |
| Related code   | `02_Showcase/THRESHOLD-INC/insidher-v1/`                  |

---

_End of report._
