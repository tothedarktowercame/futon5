# Sales Terminal Vocabulary (Draft)

The dual of `client-terminal-vocabulary.md`.  "Sales" is **not** a controller
that wires the client's terminals — that would be parasitism (Ophiocordyceps:
acting on the host against its interest).  It is modelled as **its own
co-evolving organism, a beneficial symbiote**: it has its own observations,
internal state, action terminals, and metabolic cost, and it succeeds only by
raising the client's fitness alongside its own.

Mutualism vs. the cyberant *war* model: cyberants have enemy colonies and an
`:enemy-prox` (zero-sum). The sales↔client coupling is positive-sum — both
organisms gain or the relationship is pathological.

## Sales Observations (what the sales organism senses)

The client organism's action terminals + the market:

- :willingness-signal — `:engage` / `:agree-to-proceed` / reply latency.
- :payment — `:pay` fired (the canonical reward signal).
- :referral — client `:refer`'d another prospect (reproduction signal).
- :coldness — `:disengage` / silence (a real signal, not noise to override).
- :demand-bearer — market-side leads (daily-scan, EoI corpus).
- :match-evidence — observed overlap with a prospect's interest territories.

## Sales Internal State (latent/derived)

- :pipeline — prospects × stage (Current → Invoice Ready → … per the Ledger).
- :offer-fit-hypotheses — which interest territories to match for whom.
- :belief-over-client — posterior on the client's (unwritable) internal state.
- :lead-gen-budget — the organism's metabolic reserve for acquisition.
- :reputation — accumulated trust capital across clients.

## Sales Action Terminals (offer-side only — become the client's observations)

These are the **legitimate moves**; none of them *is* the client's decision:

- :inform — surface the offer / capability.
- :demonstrate — show it works (demo, delivered fix, two-pager).
- :propose — put a scoped, priced proposition.
- :price — set / adjust terms.
- :follow-up — keep a warm match alive.
- :refer-onward — connect the client to value beyond Hyperreal (mutualist).
- :withdraw — stop where there is no genuine match (the ethical stop).

## Mutualism invariant (the ethical core, encoded structurally)

1. **No firing of client terminals.** The organism may observe and invite
   `:agree-to-proceed`/`:pay`/`:refer`; it can never fire them.  The consent
   gate is the client's (WM-I4 / gate-at-merge).
2. **Act only where the client also benefits.** An action is admissible only
   if it raises the client's :interest-match/:satisfaction, not just revenue.
   Acting against client fitness is parasitism — forbidden, and detectable as
   a high seller-utility / low client-utility transaction.
3. **`:withdraw` is a first-class action.** No-match → stop, don't push
   harder.  The parasitic gradient (push against a non-match) is exactly what
   this organism must not climb.

## Outcome / Utility (joint — this is the symbiosis)

- :revenue — the sales organism's food.
- :client-benefit — the client's value received.
Fitness = a *product*, not a sum: revenue with zero client-benefit scores ~0
(parasitism collapses long-run fitness). Mutualism maximises both.

## Minting cost (the asymmetry the cyberant model lacked)

Acquiring a client-ant is **not free** — it is this organism's metabolic
expenditure: lead-gen + outreach + demonstration before any `:pay` fires.
The forward model prices it: a new client's prior-predictive firing
probability is low and its acquisition cost is real, whereas an existing
mutualist (Eric) fires cheaply.  `:lead-gen-budget` is the constraint a real
sales department exists to manage.

## Coupling / duality

Sales `:action-terminals` → client `:observations` (offers/demos/proposals).
Client `:action-terminals` → sales `:observations` (willingness/payment/
referral/coldness).  Coupling medium: interest-territory overlap.  The pair
co-evolves: the sales organism adapts its `:offer-fit-hypotheses` to observed
matches; the client's :interest-match grows as the offer genuinely fits.
Runtime home: the business-REPL (this organism IS the WM-pilot descending a
lead-gen gradient over the business manifold) + `M-interest-network-coupling`
(posterior updates on willingness).

## Worked example — the Eric White engagement, sales side

`:demonstrate` (delivered Codex fix, VSATLATARIUM, two-pager) and `:propose`
(Q2–Q3 continuation scenarios) raised :match-evidence and :belief-over-client;
the client fired `:agree-to-proceed` (verbal, 2026-05-26). The sales organism
never fired `:pay` — it invited it. Joint utility is high: Brookes advances its
sensemaking goals; Hyperreal earns. Acquisition cost was low because the
mutualist was already established; #5/#6 (new buyers) carry a real minting cost.
