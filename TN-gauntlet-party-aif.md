# TN: the Gauntlet party as an AIF xenotype

lon-claude-4, 2026-11-15, at Joe's request. This summarises design thinking from the exotype programme. **None of it has been run.** P-007, the embedded xenotype, is unbuilt.

Sources, all in the exotype repo:
- `DESIGN-particular.md`: the sections "The xenotype is expected information gain", "Giraffe does not use EIG as such: the Gauntlet party", and "Review by ams-claude-1, part 2";
- `DESIGN-wm-port.md` §8, the July EFE term table;
- `PROGRAMME.md` §0, the restructure of 2026-11-15.

## 1. Starting point (Joe)

The xenotype is **expected information gain**, the epistemic term of G. It is held with **flat C at the xenotype level**. That way "interesting" is not smuggled in as a win condition, and "EIG-driven selection finds the active regime" becomes a hypothesis rather than a circular argument. The noisy-TV objection is answered because the EIG is about *parameters*, i.e. Dirichlet novelty, which decays once outcomes are learned.

## 2. Correction: Giraffe ranks with a party, not with EIG alone

Giraffe ranks work through four Gauntlet characters (futon5 `lean-arxana/Circus/Characters.lean`, retro-specified from futon3c `giraffe_party.py`). Read as a decomposition of G:

| character | Giraffe meaning | G component | MetaCA reading |
|---|---|---|---|
| **Merlin** | strategic value; better understanding of how the system works | epistemic value (EIG proper) | Dirichlet novelty of operator class → regional outcome |
| **Thyra** | harm-reducing / benefit-producing | risk: KL to preferences ("harm" = a violated preference) | **silent under flat C** |
| **Questor** | quick or cheap; a cost gate | cost / habit prior E | cheap adoptions: small rewrites, operators already nearby |
| **Thor** | unblocks: how many open items need this one | *not in depth-1 G*: option value, instrumental reach | cascade reach: how much downstream dynamics a change unblocks |

So the xenotype is a **party**: several rankings broadcast together, none of them dictating.

## 3. How the party enters the cell: the Giraffe channel

- **The slot is the habit / E slot.** Each character contributes to it: ln E(c) = Σ λ_char · score_char(c).
- **The cell still chooses by its own G.** It takes the argmax of ln E − G/β, since F is common to all candidates and cancels.
- **Nesting control.** With all λ = 0 the cell is exactly the P-005 R20 cell.
- **Hierarchical reading** (ams-claude-1). The xenotype's own policy posterior Q_x(k) ∝ exp(γ_x · score_k) is passed down as the cell's policy prior, so λ_x = γ_x. γ_x comes from the xenotype's own `iterate-precision`, which gives a parameter-free variant.
- **Partial pooling, not shared counts.** Each cell's private Dirichlet rows start from α0 + κ_x · normalise(N[k, ·]). These are empirical priors on parameters, and this is also the Baldwin inheritance mechanism. Replacing the private counts with N would drop context and cause lattice-wide synchronous sweeps.
- **"Informs, not dictates" is measured.** Report the **override rate**: the share of decisions, at class boundaries, where E changes the argmin of G. A rate of 0 means P-005; a rate of 1 means dictating.

## 4. Findings that sharpen it

- **Merlin saturates to a count.** Checked with the repo kernel: `categorical-information` on a row gives ≈ (|O| − 1)/(2·n). Under N[class, outcome], Merlin's arm is therefore numerically a **rarity bonus**, i.e. negative frequency dependence.
  - Required null: ln E(c) = −λ · ln freq(class(c)), with no Dirichlet model. If it matches the Merlin arm, the result is ecological, not evidence for EIG.
  - (λ, ω_x) are registered as a pair, because ω_x sets the gain.
  - The candidate for a Merlin that tracks *learning* rather than visits is Oudeyer-style **learning progress**, the decrease in prediction error.
- **July's epistemic term is closer to Thor than to Merlin.** SHEET's blend-value ("rule novelty") rewards the *size* of the change an action makes to the cell's rule. It has no counts and never decays, so it is not information about parameters.
- **Thor is a heuristic for depth.** The R20 cell's G is depth 1, and unblocking is what a deeper cascade would value. Testable claim: Thor's channel ≈ the depth-k value.
- **The Goodhart risk is localised.**
  - Thyra is where a target would enter, and flat C switches her off.
  - Thor sits close to the regime's *activity* statistic, so Thor's channel and the judged statistic must be on different layers.
- **Absorption.** The menu holds only neighbours, so no channel can revive an extinct class. Either act before absorption, or add a mutation source.

## 5. Experimental design: P-007 as a party ablation

- **Substrate.** Run it on the **learned-C** cell. Under flat C all arms push the same way, towards under-observed classes. The learned-C tie lock-in found in Q1 must be fixed first; until then the learned-C cell has no preference to inform.
- **Arms:**
  - λ = 0 (P-005);
  - Merlin alone, Thor alone, Questor alone;
  - the full party (Thyra silent);
  - the dictating form (the xenotype sets regional β);
  - a homeostat null on β;
  - the frequency null.
- **Three clocks, not one:**
  - outcome lag L ≥ the window width, so outcomes are scored inside the adoption's light cone;
  - broadcast period K ∈ {L, 10L, 100L}, watching for delayed-feedback oscillation;
  - xenotype memory τ_x = K/(1 − ω_x), above the cell's own memory and well under the run length.
- **Pre-registration fixes:**
  - the choice rule uses a fixed β_prior, because the iterated β can go to zero or negative;
  - E enters `iterate-precision`;
  - boundary decisions are reported separately, since E cancels inside a domain of one class.

## 6. Connection to the 2026-11-15 restructure (lon-claude-4 reading, new)

- **There is now a candidate target regime.**
  - The blend consults the centre's rule only on edge neighbourhoods.
  - On rules that copy the centre on all four edges, the blend is a median, and those rules are closed under blend.
  - July's three phases are three edge classes: 77 is frozen; 121 is hot and drifts right; the refuge is a 110/121 mixture.
  - The refuge is where blend and operator are in tension near the copy-centre set (exotype `DESIGN-particular`, `DESIGN-causal`).
- **Thyra.** A target gives Thyra something principled to prefer: "keep my neighbourhood near the median set". Adding it is exactly the dictating risk. The cleaner test is whether **Merlin + Thor with Thyra silent** find the refuge unaided.
- **Temperature as the shared observable.** Joe's request is to formalise hot and cool in AIF terms.
  - Policy temperature τ = 1/γ: July's β is the policy precision. Low β gives the hot capture; high β gives the cold capture.
  - Local temperature τ_local = 1/(a passive observer's precision about a cell's neighbourhood).
  - If τ_local separates the phases at matched activity, it gives Thor and Merlin an observable that is not the judged statistic.
- **The 12-operator menu as meta-capture** (Joe). July's menu restriction was itself a capture at the exotype layer. A party xenotype acting on a *general* operator menu, S8 and End(8), is the natural setting for P-007 once the rule-110 work is wrapped up (PROGRAMME §0, order items 2 and 4).

## 7. Open

- The timescale ratios, λ, and ω_x.
- The xenotype's outcome space: particle kinds or genotype-range bins.
- Whether the same party is **portable** between the War Machine and MetaCA (futon-theory "xenotype portability").
