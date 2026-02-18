# futon5 — Meta-Pattern Operators

futon5 is the meta-evolution layer of the futon stack. It runs cellular automata
experiments (MMCA) that search for edge-of-chaos dynamics, evolves "exotypes"
(kernel contexts) and "xenotypes" (evaluator populations), and maps discoveries
back onto futon3 patterns via sigils. This is where pattern *selection pressure*
gets studied computationally.

> **Stack context**: futon5 consumes patterns from futon3, runs meta-evolution,
> and proposes pattern refinements. See `../futon0/README.md` for the full stack
> diagram.

### Healthcheck (ratchet)

Run a conservative healthcheck that verifies required files/deps and runs a tiny
deterministic MMCA smoke run:

```
clj -M -m futon5.healthcheck
```

The settled-state invariants that this healthcheck is intended to protect live
in `docs/technote-settled-state.md`.

---

Concentration layer for meta-pattern operators. Patterns are lifted from
Futon3 flexiarg entries, mapped onto sigils, and executed via operators that
manipulate CA/CT/music improvisation substrates. Refer to `AGENTS.md` for the
current roadmap. The `resources/futon5/sigil_patterns.edn` registry links sigils
(e.g. 手 for BlendHand) to pattern/role metadata.

### Sigil CLI

List the metapattern allocations by running:

```
clj -M -m futon5.sigils
```

The utility reads `sigils.edn` plus `sigil_patterns.edn` and prints the sigil,
role, and pattern identifier for every reserved slot, along with the remaining
free capacity (out of 256).

### MMCA Meta-Evolve (AIF-guided)

Run the outer-loop MetaMetaCA search with AIF guidance:

```
bb -cp src:resources -m futon5.mmca.metaevolve \
  --runs 50 --length 50 --generations 50 \
  --baldwin-share 0.5 --lesion --lesion-target both \
  --aif-weight 0.2 --aif-guide --aif-guide-min 35 \
  --aif-mutate --aif-mutate-min 45 \
  --feedback-top 5 --feedback-load /tmp/mmca_feedback_next.edn \
  --feedback-edn /tmp/mmca_feedback_next.edn \
  --report /tmp/mmca_meta_report_heavy.edn
```

Meta-evolve now mutates kernel specs (blend/template/mutation/balance) instead of
only swapping fixed kernel keywords; logs include a kernel summary line. Add
`--kernel-context` to drive kernel mutation from local run heredity (ad-hoc
template logic at the kernel level). Kernel specs now include a non-allele mix
step plus coherence scoring to discourage pure confetti regimes. Pulses are
disabled by default; add `--pulses` to enable them. Add `--quiet-kernel` to bias
kernel specs toward metastability (no mix, lower mutation).
Use `--feedback-every N` and `--report-every N` to stream progress to disk
while long runs are in flight.

Save top runs as images + PDF (requires ImageMagick `convert`):

```
bb -cp src:resources -m futon5.mmca.metaevolve \
  --runs 50 --length 50 --generations 50 \
  --aif-weight 0.2 --aif-guide --aif-guide-min 35 \
  --aif-mutate --aif-mutate-min 45 \
  --save-top 5 --save-top-dir /tmp/mmca_top_runs \
  --save-top-pdf /tmp/mmca_top_runs.pdf
```

### TPG Evidence Repro Bundle

Build a deterministic evidence bundle from local TPG evolution artifacts
(`out/tpg-evo-production`):

```
bb -cp src:resources scripts/tpg_evidence_bundle.clj
```

Default outputs:

- `reports/evidence/2026-02-18-tpg-evo-repro-summary.edn`
- `reports/evidence/2026-02-18-tpg-evo-repro-report.md`
- `reports/evidence/2026-02-18-tpg-evo-repro-checksums.txt`

Use `--dry-run` to inspect without writing files.

### Exotypes (Kernel Context)

We treat kernel context as an exotype: a dynamic informational regime that
shapes how kernels mutate rather than a static environment. In practice, the
exotype is the local heredity signal (neighbor sigils + phenotype context) used
when `--kernel-context` is enabled. The loop is triadic: exotypes condition how
genotypes update, phenotypes expose mismatches, and selection rewards exotypes
that induce more informative genotype structure (not just surface outcomes).

### Xenotypes (Evaluator Population)

Xenotypes evaluate exotypes over full MMCA runs, scoring edge-of-chaos behavior
and penalizing degenerate regimes (stasis/confetti). They evolve on a slower
cadence and only update after a batch of exotype evaluations.

Run the xenotype outer loop (defaults to updating every 100 exotype runs):

```
bb -cp src:resources -m futon5.mmca.xenoevolve \
  --runs 500 --length 50 --generations 50 \
  --xeno-pop 12 --update-every 100 --tier both
```

### Render Single Runs

Render a deterministic run to a PPM image (seed-driven):

```
bb -cp src:resources -m futon5.mmca.render-cli \
  --seed 4242 --length 80 --phenotype-length 80 --generations 80 \
  --out /tmp/mmca.ppm --save-run /tmp/mmca_run_4242.edn
```

Use `convert /tmp/mmca.ppm /tmp/mmca.png` if you need PNG.
Add `--render-exotype` to render a genotype/phenotype/exotype triptych.

### Kernel Sampler

Generate a visual sampler across all legacy kernels:

```
bb -cp src:resources -m futon5.mmca.kernel-sampler \
  --seed 4242 --length 80 --phenotype-length 80 --generations 80 \
  --out-dir /tmp/mmca_kernel_sampler \
  --out-pdf /tmp/mmca_kernel_sampler.pdf \
  --summary /tmp/mmca_kernel_sampler.edn
```

Sample specific kernels (5 each):

```
bb -cp src:resources -m futon5.mmca.kernel-sampler \
  --kernels ad-hoc-template,collection-template,mutating-template \
  --samples 5 --seed 4242 --length 80 --phenotype-length 80 --generations 80 \
  --out-dir /tmp/mmca_kernel_sampler \
  --out-pdf /tmp/mmca_kernel_sampler.pdf \
  --summary /tmp/mmca_kernel_sampler.edn
```

### LLM Relay (experimental)

```
clj -M -m futon5.llm.relay --prompt path/to/system.prompt --input spec.txt
```

Use `--dry-run` to print the JSON payload instead of calling the API. The relay
reads the OpenAI API key from `~/.openai-key` when you want to POST to
`https://api.openai.com/v1/chat/completions`. The relay is inspired by
`aob-chatgpt.el` but implemented in Clojure for CLI use.

Set `FUTON5_LLM_LOG` to a file path to append JSONL entries containing model,
usage tokens, and a redacted key fingerprint for each request.

#### Groundhog Day capture

- Edit `resources/demos/groundhog_day.prompt` (system instructions) and
  `resources/demos/groundhog_day_input.txt` (user brief) so the scenario matches
  the loop you want Futon3 to replay.
- Dry run the payload to verify formatting:
  ```
  clj -M -m futon5.llm.relay \
      --prompt resources/demos/groundhog_day.prompt \
      --input resources/demos/groundhog_day_input.txt \
      --dry-run
  ```
- When satisfied, drop `--dry-run` and redirect the real response into a file
  that Futon3 will consume, e.g. `resources/demos/groundhog_day_raw.json`. That
  JSON becomes the deterministic source for the MUSN/Groundhog Day demo.
