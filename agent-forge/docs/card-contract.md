# Definition of Done: The Card Contract

Every agent-forge delegation card MUST carry a DONE section before
dispatch. A card without one does not ship. This file is the schema and
the law; qc and reviewers reject cards that dodge it.

Why: agents chase passing conditions (the "agent school" problem). If
the finish line is not defined in machine-checkable terms before launch,
the work produces process, not value [qYe1GsMRElw; wiki/definition-of-done].

## The DONE schema

Every card's DONE section contains, in this order:

1. **Machine-checkable acceptance criteria.** Each criterion must be
   verifiable by a command, a file assertion, or a count. "Looks good"
   is not a criterion. Format: `DONE IF <checkable statement>`.
   - File existence and counts: "DONE IF renders/manifest.json lists
     5 entries with qc_status passed"
   - Measurable qualities: "DONE IF python3 -m py_compile tools/*.py
     exits 0" or "DONE IF the HTML contains zero em-dash characters"
   - Behavioral: "DONE IF ffprobe reports 30-70s and 1080x1920"
     Minimum 3, maximum 7 criteria. More than 7 means the card is two
     cards.

2. **The second-best-engineer test.** State who could maintain the
   output: "DONE IF a competent non-author reviewer can open any file
   produced and explain its purpose in under 20 minutes." For code:
   note file sizes, function limits, reuse boundaries. Complexity
   budgets belong here (e.g. no file over 400 lines, no function over
   50 [wiki/definition-of-done]).

3. **The unplug test.** What breaks when this ships and the author
   disappears: "DONE IF the deliverable runs from a clean checkout
   with only the README's install steps." If it needs the author's
   machine, it is not done.

4. **Business measure linkage.** One line tying the deliverable to a
   cash-register metric or a portfolio goal. If no link exists, the
   card says so explicitly ("infrastructure; no direct metric") rather
   than inventing one.

## Verification rules

- The DONE section is written BEFORE dispatch, by the dispatcher.
- The executor may not weaken, reinterpret, or re-scope criteria; if a
  criterion proves wrong, the executor stops and reports rather than
  quietly shipping around it.
- Completion claims must quote command output for every machine-
  checkable criterion. "All tests pass" without the output is a
  failed card.
- The reviewer (human or council) re-runs at least one criterion.

## Anti-gaming

Every metric gets gamed eventually [qYe1GsMRElw 7:05]. Countermeasures:

- Prefer counts that are expensive to fake (real file contents, real
  command output) over counts that are cheap to fake (line counts,
  TODO markers added/removed).
- Tests must protect behavior, not bless the answer: if the executor
  wrote the test, the reviewer spot-checks it against the spec.
- The judge is never the worker: card completion is verified by the
  parent or a second pass, never solely by the executor's self-report.

## Card template snippet

    ## DONE
    - DONE IF <criterion 1 (command/file/count)>
    - DONE IF <criterion 2>
    - DONE IF <criterion 3>
    - Maintainable by: <who, and what they need to understand it>
    - Unplug test: <clean-checkout condition>
    - Business link: <metric or explicit "no direct metric">

## Provenance

Pattern: Runable "agents that finish" thesis + forge qc gates.
Sources: [qYe1GsMRElw 4:28, 9:59]; wiki/definition-of-done;
Nate Herk roast-council verification discipline [iTY8Q449YNQ].
