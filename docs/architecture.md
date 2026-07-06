# Cognitive OS Architecture

## System Overview

```mermaid
graph TB
    subgraph "Cognitive OS v3.0"
        AGENTS[AGENTS.md<br/>Master Specification<br/>16 Sections]

        subgraph "Harness Adapters"
            DROID[.factory/AGENTS.md<br/>Factory Droid]
            CLAUDE[CLAUDE.md<br/>Claude Code]
            CURSOR[.cursorrules<br/>Cursor]
            CODEX[CODEX.md<br/>Codex CLI]
            GEMINI[GEMINI.md<br/>Gemini CLI]
        end

        subgraph "10-Layer Cognitive Stack"
            L1[Layer 1: Intake & Triage]
            L2[Layer 2: Reasoning Mode]
            L3[Layer 3: Context Budget]
            L4[Layer 4: Diff-Aware Context]
            L5[Layer 5: Tool Result Blindness]
            L6[Layer 6: Semantic Search]
            L7[Layer 7: Generated File Awareness]
            L8[Layer 8: Phased Execution]
            L9[Layer 9: Rollback Planning]
            L10[Layer 10: Dependency Discipline]
        end

        subgraph "Core Systems"
            MEM[4-Layer Memory Stack<br/>Working/Episodic/Semantic/Procedural]
            VERIFY[Verification System<br/>Chain + Self-Healing]
            SWARM[Swarm Coordination<br/>DAG Execution]
            SEC[Adversarial Security<br/>Pre-Commit Checklist]
            ECON[Economic Intelligence<br/>Token Economics + ROI]
            LEARN[Self-Improvement<br/>Learning Loop]
        end
    end

    AGENTS --> DROID
    AGENTS --> CLAUDE
    AGENTS --> CURSOR
    AGENTS --> CODEX
    AGENTS --> GEMINI

    L1 --> L2 --> L3 --> L4 --> L5 --> L6 --> L7 --> L8 --> L9 --> L10

    AGENTS --> L1
    AGENTS --> MEM
    AGENTS --> VERIFY
    AGENTS --> SWARM
    AGENTS --> SEC
    AGENTS --> ECON
    AGENTS --> LEARN
```

## Task Complexity Routing

```mermaid
flowchart LR
    INPUT[User Request] --> CLASSIFY{Complexity<br/>Classifier}
    CLASSIFY -->|T0| FAST[Fast Model<br/>< 2s]
    CLASSIFY -->|T1| STD[Standard Model<br/>Sequential]
    CLASSIFY -->|T2| HEAVY[Heavy Model<br/>Plan-Execute-Verify]
    CLASSIFY -->|T3| ENSEMBLE[Ensemble<br/>Adversarial Review]
    CLASSIFY -->|T4| HUMAN[Human-in-Loop<br/>Explicit Approval]
```

## Verification Chain

```mermaid
flowchart TB
    TASK[Task Complete Claim] --> TYPE{Type Check}
    TYPE -->|pass| LINT{Lint Check}
    TYPE -->|fail| FIX[Fix Errors]
    LINT -->|pass| UNIT{Unit Tests}
    LINT -->|fail| FIX
    UNIT -->|pass| INT{Integration Tests}
    UNIT -->|fail| FIX
    INT -->|pass| BUILD{Build}
    INT -->|fail| FIX
    BUILD -->|pass| SEC{Security Scan}
    BUILD -->|fail| FIX
    SEC -->|pass| DONE[Verified Complete]
    SEC -->|fail| FIX
    FIX --> TYPE
```

## File Dependencies

| File                 | Depends On  | Used By       |
| -------------------- | ----------- | ------------- |
| `AGENTS.md`          | (root spec) | All adapters  |
| `.factory/AGENTS.md` | `AGENTS.md` | Factory Droid |
| `CLAUDE.md`          | `AGENTS.md` | Claude Code   |
| `.cursorrules`       | `AGENTS.md` | Cursor        |
| `CODEX.md`           | `AGENTS.md` | Codex CLI     |
| `GEMINI.md`          | `AGENTS.md` | Gemini CLI    |
