# futon5

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

Save top runs as images + PDF (requires ImageMagick `convert`):

```
bb -cp src:resources -m futon5.mmca.metaevolve \
  --runs 50 --length 50 --generations 50 \
  --aif-weight 0.2 --aif-guide --aif-guide-min 35 \
  --aif-mutate --aif-mutate-min 45 \
  --save-top 5 --save-top-dir /tmp/mmca_top_runs \
  --save-top-pdf /tmp/mmca_top_runs.pdf
```

### Render Single Runs

Render a deterministic run to a PPM image (seed-driven):

```
bb -cp src:resources -m futon5.mmca.render-cli \
  --seed 4242 --length 80 --phenotype-length 80 --generations 80 \
  --out /tmp/mmca.ppm --save-run /tmp/mmca_run_4242.edn
```

Use `convert /tmp/mmca.ppm /tmp/mmca.png` if you need PNG.

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
