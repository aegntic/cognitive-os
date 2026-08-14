package com.thresholdinc.insidher.core

import com.thresholdinc.insidher.contracts.DepositEvidenceType
import com.thresholdinc.insidher.contracts.DepositStatus
import com.thresholdinc.insidher.contracts.HumanDecision
import com.thresholdinc.insidher.contracts.OrchestratorAction
import com.thresholdinc.insidher.contracts.PersonaProfile
import com.thresholdinc.insidher.contracts.ThreadContext
import com.thresholdinc.insidher.contracts.ThreadState
import com.thresholdinc.insidher.contracts.WorkerOutput
import com.thresholdinc.insidher.core.orchestrator.InquiryOrchestrator
import com.thresholdinc.insidher.core.workers.BookingWorker
import com.thresholdinc.insidher.core.workers.DepositEvidenceInput
import com.thresholdinc.insidher.core.workers.MemoryWorker
import com.thresholdinc.insidher.core.workers.PersonaWorker
import com.thresholdinc.insidher.core.workers.TimingKind
import com.thresholdinc.insidher.core.workers.TimingPolicy
import com.thresholdinc.insidher.core.workers.TimingWorker
import com.thresholdinc.insidher.contracts.AvailabilityPolicy
import com.thresholdinc.insidher.core.safety.AvailabilityChecker
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("TimingWorker / TimingPolicy (m1)")
class TimingWorkerTest {

    @Test
    fun `initial delay in 45-180s range and non-fixed`() {
        val worker = TimingWorker(random = Random(42))
        val policy = AvailabilityPolicy(timezone = "UTC")
        val now = Instant.parse("2026-07-16T10:00:00Z")
        val samples = (1..20).map {
            worker.compute(TimingKind.INITIAL, policy, now).output.delayMs
        }
        assertThat(samples).allMatch { it in 45_000L..180_000L }
        assertThat(samples.distinct().size).isGreaterThan(1)
    }

    @Test
    fun `follow-up delay in 20-90s and batch gap 3-12s`() {
        val worker = TimingWorker(random = Random(7))
        val policy = AvailabilityPolicy(timezone = "UTC")
        val now = Instant.parse("2026-07-16T10:00:00Z")
        val out = worker.compute(TimingKind.FOLLOW_UP, policy, now).output
        assertThat(out.delayMs).isBetween(20_000L, 90_000L)
        assertThat(out.batchGapMs).isBetween(3_000L, 12_000L)
    }

    @Test
    fun `quiet hours should hold`() {
        val worker = TimingWorker()
        val policy = AvailabilityPolicy(timezone = "UTC")
        val quiet = Instant.parse("2026-07-16T23:30:00Z")
        val d = worker.compute(TimingKind.INITIAL, policy, quiet)
        assertThat(d.shouldHold).isTrue()
    }

    @Test
    fun `default policy ranges match spec`() {
        val p = TimingPolicy.DEFAULT
        assertThat(p.initialMinMs).isEqualTo(45_000L)
        assertThat(p.initialMaxMs).isEqualTo(180_000L)
        assertThat(p.followUpMinMs).isEqualTo(20_000L)
        assertThat(p.followUpMaxMs).isEqualTo(90_000L)
        assertThat(p.batchGapMinMs).isEqualTo(3_000L)
        assertThat(p.batchGapMaxMs).isEqualTo(12_000L)
    }
}

@DisplayName("MemoryWorker (m1)")
class MemoryWorkerTest {

    @Test
    fun `store retrieve per thread isolated`() {
        val m = MemoryWorker()
        val now = Clock.System.now()
        m.store("t1", "preferred_time", "thursday", now)
        m.store("t2", "preferred_time", "friday", now)
        assertThat(m.retrieve("t1", now).entries.single().value).isEqualTo("thursday")
        assertThat(m.retrieve("t2", now).entries.single().value).isEqualTo("friday")
    }

    @Test
    fun `natural hints from observed text`() {
        val m = MemoryWorker()
        val now = Clock.System.now()
        m.observe("t1", "Hey free Thursday for a massage?", now)
        val hints = m.naturalHints("t1", now)
        assertThat(hints).isNotEmpty
        assertThat(hints.joinToString(" ").lowercase()).containsAnyOf("thursday", "massage")
    }

    @Test
    fun `decay reduces confidence over time`() {
        val m = MemoryWorker(halfLifeMs = 1_000L, minConfidence = 0.01)
        val t0 = Instant.fromEpochMilliseconds(1_000_000)
        m.store("t1", "k", "v", t0, confidence = 1.0)
        val later = Instant.fromEpochMilliseconds(1_000_000 + 2_000)
        m.decay("t1", later)
        val conf = m.retrieve("t1", later).entries.single().confidence
        assertThat(conf).isLessThan(0.5)
    }
}

@DisplayName("PersonaWorker (m1)")
class PersonaWorkerM1Test {

    @Test
    fun `one persona per thread enforced`() {
        val p = PersonaWorker(FakeInferenceProvider())
        p.bindPersona("t1", "p1")
        assertThatThrownBy { p.bindPersona("t1", "p2") }
            .isInstanceOf(IllegalArgumentException::class.java)
        p.bindPersona("t1", "p1") // same ok
    }

    @Test
    fun `draft uses vocabulary and scrub path`() = runBlocking {
        val p = PersonaWorker(FakeInferenceProvider("As an AI I can help. Sure hun"))
        val out = p.draft(
            PersonaProfile(name = "Anita", tone = "warm", vocabulary = listOf("hun", "xx")),
            "hey",
            emptyList(),
            threadId = "t1",
            personaId = "p1",
        )
        assertThat(out.responseText.lowercase()).doesNotContain("as an ai")
        assertThat(out.responseText).isNotBlank
    }

    @Test
    fun `hasAiOrCorporateTell detects`() {
        val p = PersonaWorker(FakeInferenceProvider())
        assertThat(p.hasAiOrCorporateTell("Thank you for reaching out")).isTrue()
        assertThat(p.hasAiOrCorporateTell("hey free thursday?")).isFalse()
    }
}

@DisplayName("BookingWorker deposit flow (m2)")
class BookingWorkerTest {

    private fun ctx(state: ThreadState = ThreadState.CONVERSING) = ThreadContext(
        id = "t1",
        state = state,
        revision = 1,
        personaId = "p1",
        clientPhone = "+61400000000",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    private fun persona() = PersonaProfile(
        name = "Anita",
        tone = "warm",
        depositWording = "Just a small deposit locks it in xx",
        offerings = listOf("companionship"),
    )

    @Test
    fun `booking interest moves to DEPOSIT_REQUESTED`() {
        val w = BookingWorker()
        val eval = w.evaluate(ctx(), "Are you free Thursday to book?", persona())
        assertThat(eval.targetState).isEqualTo(ThreadState.DEPOSIT_REQUESTED)
        assertThat(eval.output.depositRequested).isTrue()
        assertThat(eval.depositPrompt).contains("deposit")
    }

    @Test
    fun `deposit claim without evidence does not reach HUMAN_REVIEW`() {
        val w = BookingWorker()
        w.evaluate(ctx(), "Can I book Friday?", persona())
        val claim = w.evaluate(ctx(ThreadState.DEPOSIT_REQUESTED), "I just paid the deposit", persona())
        assertThat(claim.targetState).isNull()
        assertThat(claim.chainToHumanReview).isFalse()
    }

    @Test
    fun `evidence required for DEPOSIT_PENDING then HUMAN_REVIEW`() {
        val w = BookingWorker()
        w.evaluate(ctx(), "book me for Saturday", persona())
        val pending = w.applyEvidence(
            ctx(ThreadState.DEPOSIT_REQUESTED),
            DepositEvidenceInput(
                type = DepositEvidenceType.MANUAL_FLAG,
                amount = 50.0,
                markVerified = false,
            ),
        )
        assertThat(pending.targetState).isEqualTo(ThreadState.DEPOSIT_PENDING)
        assertThat(pending.output.depositStatus).isEqualTo(DepositStatus.RECEIVED)

        val verified = w.applyEvidence(
            ctx(ThreadState.DEPOSIT_PENDING),
            DepositEvidenceInput(
                type = DepositEvidenceType.STRIPE_WEBHOOK,
                amount = 50.0,
                evidenceRef = "pi_test_123",
                markVerified = true,
            ),
        )
        assertThat(verified.targetState).isEqualTo(ThreadState.HUMAN_REVIEW)
        assertThat(verified.output.depositStatus).isEqualTo(DepositStatus.VERIFIED)
        assertThat(verified.depositRecord?.evidenceType).isEqualTo(DepositEvidenceType.STRIPE_WEBHOOK)
    }

    @Test
    fun `stripe without ref rejected`() {
        val w = BookingWorker()
        assertThatThrownBy {
            w.applyEvidence(
                ctx(ThreadState.DEPOSIT_REQUESTED),
                DepositEvidenceInput(type = DepositEvidenceType.STRIPE_WEBHOOK, amount = 10.0),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `polite decline copy`() {
        val w = BookingWorker()
        assertThat(w.politeDecline().lowercase()).containsAnyOf("care", "understand", "worries")
    }
}

@DisplayName("Orchestrator m1/m2 integration")
class OrchestratorM1M2Test {

    private fun orch() = InquiryOrchestrator.create(FakeInferenceProvider("Sure hun, Thursday works"))

    private fun base(
        state: ThreadState = ThreadState.CONVERSING,
        id: String = "t1",
    ) = ThreadContext(
        id = id,
        state = state,
        revision = 1,
        personaId = "p1",
        clientPhone = "+61400000000",
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    private fun persona() = PersonaProfile(
        name = "Anita",
        tone = "warm casual",
        vocabulary = listOf("hun", "xx"),
        depositWording = "Small deposit locks it in xx",
    )

    private fun inbound(body: String, threadId: String = "t1") =
        com.thresholdinc.insidher.contracts.ClientMessage(
            threadId = threadId,
            body = body,
            timestamp = Clock.System.now(),
            phoneNumber = "+61400000000",
        )

    @Test
    fun `pipeline still safety memory persona safety booking timing`() = runBlocking {
        val r = orch().handleInbound(base(), inbound("Hey free Thursday?"), persona())
        assertThat(r.trace.steps).containsSubsequence(
            "safety_inbound",
            "memory",
            "persona",
            "safety_outbound",
            "booking",
            "timing",
        )
    }

    @Test
    fun `timing uses non-fixed schedule delayed`() = runBlocking {
        val r = orch().handleInbound(base(), inbound("hello there friend"), persona())
        val delayed = r.actions.filterIsInstance<OrchestratorAction.ScheduleDelayed>()
        assertThat(delayed).isNotEmpty
        assertThat(delayed.first().delayMs).isBetween(45_000L, 180_000L)
    }

    @Test
    fun `deposit evidence advances to HUMAN_REVIEW without final SMS`() = runBlocking {
        val o = orch()
        val first = o.handleInbound(base(), inbound("Can I book Friday night?"), persona())
        assertThat(first.context.state).isEqualTo(ThreadState.DEPOSIT_REQUESTED)

        val withEvidence = o.handleInbound(
            first.context,
            inbound("I paid already"),
            persona(),
            depositEvidence = DepositEvidenceInput(
                type = DepositEvidenceType.STRIPE_WEBHOOK,
                amount = 50.0,
                evidenceRef = "pi_abc",
                markVerified = true,
            ),
        )
        // PENDING then HUMAN_REVIEW
        assertThat(withEvidence.context.state).isEqualTo(ThreadState.HUMAN_REVIEW)
        assertThat(withEvidence.smsAllowed).isFalse()
        assertThat(withEvidence.outboundMessages).isEmpty()
        assertThat(withEvidence.actions).contains(OrchestratorAction.WaitForHuman(withEvidence.context.id))
    }

    @Test
    fun `approve only sends confirmation and reject is polite`() {
        val o = orch()
        val review = base(ThreadState.HUMAN_REVIEW)
        val reject = o.applyHumanDecision(review, HumanDecision.Reject(Clock.System.now()))
        assertThat(reject.context.state).isEqualTo(ThreadState.ENDED)
        assertThat(reject.outboundMessages.first().body.lowercase()).doesNotContain("you're all set")
        assertThat(reject.trace.steps).contains("polite_decline")

        val approve = o.applyHumanDecision(review, HumanDecision.Approve(Clock.System.now()))
        assertThat(approve.context.state).isEqualTo(ThreadState.CONFIRMED)
        assertThat(approve.smsAllowed).isTrue()
        assertThat(approve.outboundMessages.first().body.lowercase()).contains("set")
    }
}
