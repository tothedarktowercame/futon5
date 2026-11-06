# Peeragogy v4 pattern language: signed-graph balance check

M-formal-patterns S4. Source: Peeragogy/Peeragogy.github.io `src/*.org`
at commit `3ba572c553b1a45627e5abe4927fd42784b12d2f`, vendored under
`vendor/peeragogy-pattern-language/src` (11 files tagged `#+roam_tags: PAT`).

## Sign rule (stated, not fitted)

An edge A→B is **negative** when the linking paragraph frames B as a
substitute/retirement target — it contains one of: `no longer`, `out-of-date`, `stop doing`, `instead of`, `rather than`, `retire`, `supersede` — else **positive** (chaining confirms salience). Every edge carries
its verbatim quote; audit the signs against the quotes.

## Patterns (11)

- `carrying` — Carrying capacity
- `distributed_roadmap` — Emergent Roadmap
- `heartbeat` — Heartbeat
- `newcomer` — Newcomer
- `pattern_audit` — Pattern Audit Routine
- `peeragogy` — Peeragogy
- `reduce` — Reduce, reuse, recycle
- `roadmap` — Roadmap
- `scrapbook` — Scrapbook
- `specific` — A specific project
- `wrapper` — Wrapper

## Edges (14 directed citations → 14 undirected signed edges)

- carrying → roadmap **positive** (§Solution                                                             :key:) [Use the project [[file:roadmap.org][Roadmap]] to make note of obstacles.]
- heartbeat → carrying **positive** (§Rationale) […an quick way to do a reality check and find the project's strongest pulse: the activities that sustain a healthy project should sustain us, too (cf. [[file:pattern-carrying.org][Carrying capacity]]).…]
- heartbeat → scrapbook **negative** (§Resolution) […On the other hand, if a specific activity is no longer sustaining the project, stop doing it, much as you would move an out-of-date pattern to the [[file:scrapbook.org][Scrapbook]] in order to make room for other concerns. The power of the Heartbeat is that the project can be as focused and intensive as it needs to…]
- newcomer → reduce **positive** (§Problem) […of ideas that the old-timers have never considered -- or they may think they have new ideas, which are actually a different take on an old idea; see [[file:reduce_reuse_recycle.org][Reduce, reuse, recycle]]. People who are new to the project can tell you what makes their participation difficult. Since you're learning…]
- newcomer → scrapbook **positive** (§Solution) […with particular emphasis on educational applications, is surveyed in [5]. One method for doing the reflection/assessment step is presented in the [[file:scrapbook.org][Scrapbook]] pattern. Be flexible: networked attention (even more so than rigid cycles [3]) leads to new ways of knowing and expanded access to…]
- peeragogy → specific **positive** (§Solution) […in interconnected networks that may reach all over the world. Importantly, while people involved in a peeragogical process may be collaborating on [[file:a_specific_project.org][A specific project]], they don't have to be direct collaborators outside of the learning context or co-located in time or space. Just as…]
- roadmap → heartbeat **positive** (§Rationale) […was a "crowdsourced" outline of the first edition of the /Peeragogy Handbook/. Later, it took the form of a schedule of meetings following a regular [[file:heartbeat.org][Heartbeat]], supplemented by a list of upcoming deadlines. Most recently, our roadmap is expressed in the emergent objectives collected at the end…]
- roadmap → newcomer **positive** (§Solution) […Building a guide to the goals, activities, experiments and working methods can help [[file:newcomer.org][Newcomers]] and old-timers alike understand their relationship with the project. It may combine features of a manifesto, a syllabus, and an issue…]
- roadmap → wrapper **positive** (§Solution) […from /here/ to /there/. By this same token, any given version of the roadmap is seen as fallible. In lieu of widespread participation, the project's [[file:wrapper.org][Wrapper]] should attempt to synthesize an accurate roadmap that is informed by participants' behavior, and should help moderate in case of conflict.…]
- scrapbook → reduce **positive** (§Solution) […along with a communal one. Furthermore, you don't need to limit yourself to /your own/ creativity: include interesting ideas from other sources (see [[file:reduce_reuse_recycle.org][Reduce, reuse, recycle]]). In some cases a designated Wrapper may have to do further work to elicit and organize contributions.…]
- specific → distributed_roadmap **positive** (§What's Next, NEXT-STEPS) [Furthermore, each project, insofar as it uses patterns in the style we’ve discussed, should have an emergent roadmap comprised of ‘next steps’. Here’s ours: - [[file:distributed_roadmap.org][Emergent Roadmap]]]
- specific → pattern_audit **positive** (§What's Next, NEXT-STEPS) [Each project connected with the Peeragogy Project should be described with one or more patterns, each with specific, tangible "what's next" steps.[fn:3] The [[file:pattern_audit.org][Pattern Audit Routine]] can help make these "what's next" steps concrete.]
- specific → roadmap **positive** (§Solution) […that your "specific project" is too large to tackle directly. In this case, you will have to become even more specific. Maintaining a project [[file:roadmap.org][Roadmap]] can help keep track of the smaller pieces and the bigger picture.…]
- wrapper → scrapbook **positive** (§Solution) […any problems they are running into. Between meetings, each participant is advised to maintain some sort of "learning log" in the form of a personal [[file:scrapbook.org][Scrapbook]], so that outstanding concerns are surfaced and available to discuss.…]

## Cycles (10): 6 frustrated

- Carrying capacity–Heartbeat–Roadmap — 0 negative balanced
- Newcomer–Reduce, reuse, recycle–Scrapbook — 0 negative balanced
- Newcomer–Roadmap–Wrapper–Scrapbook — 0 negative balanced
- Newcomer–Reduce, reuse, recycle–Scrapbook–Wrapper–Roadmap — 0 negative balanced
- Heartbeat–Roadmap–Newcomer–Scrapbook — 1 negative **FRUSTRATED**
- Heartbeat–Roadmap–Wrapper–Scrapbook — 1 negative **FRUSTRATED**
- Carrying capacity–Heartbeat–Scrapbook–Newcomer–Roadmap — 1 negative **FRUSTRATED**
- Carrying capacity–Heartbeat–Scrapbook–Wrapper–Roadmap — 1 negative **FRUSTRATED**
- Heartbeat–Roadmap–Newcomer–Reduce, reuse, recycle–Scrapbook — 1 negative **FRUSTRATED**
- Carrying capacity–Heartbeat–Scrapbook–Reduce, reuse, recycle–Newcomer–Roadmap — 1 negative **FRUSTRATED**

## Verdict

The language is **unbalanced**: 6 of 10 simple cycles are frustrated (odd negative parity). Every
frustrated cycle traverses the Heartbeat–Scrapbook edge, whose text
is the catalog's one substitution/retirement link ("if a specific
activity is no longer sustaining the project, stop doing it … move
an out-of-date pattern to the Scrapbook"). Under the mission's
semantics each such cycle is a self-undermining validation loop:
evidence routed around it disconfirms the starting pattern, so no
stable joint verdict exists. The mission's predicted meta-pattern
intervention applies: flip one validation-link on each frustrated
cycle — e.g. re-read the retirement edge as confirming the Heartbeat
mechanism's salience (it is what *effects* the retirement) rather
than superseding the retired activity — and parity becomes even.
