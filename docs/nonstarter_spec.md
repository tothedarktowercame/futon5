# Nonstarter.org (Draft Specification)

Definition (from common usage):
on-starter
1. A person or animal that fails to take part in a race.
2. A person, plan, or idea that has no chance of succeeding or being effective.

Working thesis:
Nonstarter is pirate art for pleasure, not for profit. It uses the
"nonstarter" term as a reversal: we fund the unlikely and the
non-teleological on purpose.

Status:
- Draft specification
- Incorporation pending

## The 7 Draft Commandments

1) Kickstarter.com is famous, but for profit.
2) Projects like Diaspora got famous and funded through Kickstarter.
3) We can build a not-for-profit site that does what Kickstarter does,
   but is more fun and better.
4) People upload project descriptions as usual, but money is pooled.
   Donors give to the pool, and the crowd decides what gets funded.
   Voting is StackExchange/OSQA-like; one vote per day keeps it addictive.
   If a project is voted up and funds are available, it is funded automatically.
5) Philanthropy should be legible: profiles can show donations (optional),
   votes (optional), and track record as proposer/implementor.
6) Additional votes can be purchased or earned:
   - example: 1-year membership at $50 gives 5 votes per day
   - example: if your idea gets funded, you gain an extra vote
7) Bootstrapping:
   - either launch on Kickstarter for publicity
   - or let Nonstarter fund itself with a "fund this site for a year" proposal
   - likely need seed funds for non-profit incorporation

## Core Mechanics

Pool Funding:
- Donations go into a shared pool, not to a specific project.
- The pool is allocated by crowd voting and availability of funds.

Voting:
- Default: 1 vote per day.
- Optional: membership tiers, earned votes for funded proposals.

Auto-Funding:
- If project score is above threshold and funds are available, it is funded.
- Funding is automatic once the criteria are met.

Profiles:
- Optional disclosure of total donations.
- Optional disclosure of voting history.
- Track record of proposals and implementations.

## Conceptual Clarifications

Two ideas are related but not identical:
- Non-teleology: no guaranteed success path, no optimization as goal.
- Anti-selection: preference for unlikely, strange, or non-obvious ideas.

Without guardrails, a non-teleological system can still drift toward
safe bets via crowd dynamics. The mechanics below must explicitly resist
"sticky stripes" (popularity capture).

## Dynamics and Guardrails

Thresholds are power:
- Funding thresholds define culture.
- Thresholds must be adjustable and governed, not fixed forever.

Auto-funding implies irreversibility:
- Once funded, reversal is costly.
- This pushes pressure upstream into voting and threshold design.

Vote decay / forgetting (required):
- Votes should decay or reset over time to prevent early lock-in.
- This keeps weirdness viable and avoids permanent capture.

Legibility should privilege actions over identity:
- Implementation track record should weigh more than donation totals.
- Voting history should be optional, and defaults should avoid performative gaming.

## Governance (Minimum, Dynamic)

- Public criteria for automatic funding.
- Transparent pool balance and allocation rules.
- Simple enforcement against spam or abuse.
- Time-based resets or phases (Return-to-I, Phase-Switch, Noise Budget).

## AIF / Meta-Pattern Binding

The AIF layer should not just observe; it should constrain:
- define which invariants are enforced mechanically
- separate simulation/analysis from live governance

If AIF metrics only describe, they can be ignored.
If they constrain, they must be normatively justified.

## Open Questions (Reframed)

- What failure size is acceptable per cycle?
- What must never be optimized for?
- Which manipulations are acceptable play?
- Which signals should be impossible to accumulate?
- How often should governance be allowed to break itself?

## References

- http://blog.p2pfoundation.net/crowdfunding-as-the-trojan-horse-of-the-commons/2011/07/21
- https://www.crowdtilt.com/learn
- http://brendanbaker.tumblr.com/post/17334880712/crashing-planes-and-early-investors-why-you-should

## Contact

We are not hiring. There is a mailing list. Also check starter.org.

## Brainstorm: Nonstarter x AIF x Meta-Patterns

These notes connect Nonstarter to AIF, MMCA, and the meta-pattern layer.

Mapping ideas:
- AIF as "global rule" (non-teleology).
  - Invariant: refuse capture; success is not optimization.
  - Mechanism: pooled funding + auto-funding thresholds.
- Pool as phenotype, proposals as genotype.
  - Genotype: project proposals (sigil/pattern strings).
  - Phenotype: funded portfolio over time.
  - Genotype-gate: proposals "express" only when community signal allows.
- Votes as pheromone trails.
  - Trails are temporary, not permanent status.
  - "Sticky stripes" = popularity capture to avoid.
- Tension calculus alignment.
  - stability/change: avoid frozen funding cycles without chaos.
  - diversity/convergence: prevent monocultures.
  - trail-following vs wandering: focus without lock-in.
  - resource discovery vs exploitation: reward new/odd without burn.
- Meta-pattern governance primitives.
  - Return-to-I: periodic reset of pool allocation.
  - Noise Budget: capped funding for wild ideas.
  - Phase-Switch: rotate explore/exploit modes.
  - Accumulator: pooled fund operator.
  - Facet: multiple scoring lenses (good, weird, feasible).
  - WhiteSpaceScout: detect under-served domains and bias funding.

Concrete experiments:
- Simulate Nonstarter with MMCA: proposals as sigil strings, voting kernels as rules.
- Use AIF scoring on portfolio health (diversity, trail strength, lesion recovery).
- CT diagram for funding pipeline (donation -> pool -> vote -> threshold -> fund -> feedback).

Open design questions (AIF-aligned):
- How to prevent "sticky stripe" popularity capture?
- What minimum diversity must be preserved each cycle?
- Which AIF invariant is sacred: non-teleology, non-extraction, or community joy?

## Positioning Note

Nonstarter is closer to:
- a commons allocator than a marketplace
- a portfolio engine than a campaign platform
- a cultural instrument than a funding tool

## Billing and Funding Constraints

This section clarifies what can be funded without reintroducing
teleology or capture.

### What is not billable

Nonstarter does not fund:
- ownership or control of Nonstarter
- discretionary decision-making power
- "founder compensation" or abstract stewardship
- guaranteed outcomes (growth, adoption, success)

### The billable unit: funded extension contract

The clean object is a time-bounded, capability-scoped extension:
- finite duration (e.g., 3 or 6 months)
- concrete deliverables
- separable from governance
- usable by the community regardless of who built it

This is public infrastructure contracting, not startup work.

### Specification requirements for billing

Any billable spec must answer:

1) Capability added
   - example: voting algorithm v2 with decay
   - example: pool accounting + public ledger UI
   - example: proposal lifecycle tooling
   - example: simulation / AIF analytics dashboard
   - example: anti-capture mechanisms (resets, phase switches)

2) Explicitly out of scope
   - no policy decisions
   - no content moderation
   - no proposal prioritization
   - no fundraising guarantees

3) End artifacts
   - code modules
   - documentation
   - deployed service
   - simulation reports
   - governance-neutral tooling

4) Time and compensation model
   - fixed monthly retainer for defined capacity
   - or fixed-price milestone payouts
   - or capped hours with published rate
   - payment is for availability + output, not authority

5) Renewal decision
   - re-proposal to the pool
   - automatic expiry unless re-funded
   - no implicit continuation

### Ethos alignment

The alignment hinge:
You are not funding a person; you are funding a tool that can be built
by anyone (even if it happens to be built by you).

Nonstarter can fund:
- maintenance
- extensions
- instrumentation
- experimentation

It cannot fund:
- personal livelihood as such
- strategic direction
- founder privilege

### Minimal spec skeleton (conceptual)

Title: Nonstarter Pool Allocation Engine v1
Duration: 3 months
Budget: £3,000
Capability added: X
Out of scope: Y
Artifacts: Z
Success condition: artifacts delivered; system still operable
Failure tolerance: feature may be unused or rejected

Missing by design: success metrics, growth targets, ROI.

### The real constraint

The symbolic constraint is the hardest one:
- your proposal can fail
- someone else can out-vote you
- your contract can expire
- the system must survive without you

If those are true in the spec, compensation is not a contradiction; it
is labor for shared infrastructure.
