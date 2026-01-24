# Prototype Xenotype Wirings

These 8 prototypes demonstrate one wiring per hexagram family.

## :xenotype-001: Creative + Expand

**Family:**  | **Hexagram:** 1 | **Energy:** :peng

Creative expansion: when local diversity is low, mutate away from neighbors; otherwise XOR to differentiate

```mermaid
graph LR

    %% Nodes
    ctx-pred([context-pred])
    ctx-self([context-self])
    ctx-succ([context-succ])
    neighbors([context-neighbors])
    diversity[diversity]
    mutate[mutate]
    xor[bit-xor]
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| diversity
    ctx-self -->|sigil → sigil| mutate
    val_G__202((0.3))
    val_G__202 -->|rate| mutate
    ctx-pred -->|sigil → a| xor
    ctx-succ -->|sigil → b| xor
    diversity -->|score → score| choose
    val_G__203((0.5))
    val_G__203 -->|threshold| choose
    xor -->|result → above| choose
    mutate -->|result → below| choose
```

---

## :xenotype-038: Treading + Yield

**Family:** Receptive | **Hexagram:** 10 | **Energy:** :lu

Receptive yielding: preserve self when aligned with neighbors, blend toward consensus when not

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    neighbors([context-neighbors])
    consensus[majority]
    similarity[similarity]
    blend[blend]
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| consensus
    ctx-self -->|sigil → a| similarity
    consensus -->|result → b| similarity
    ctx-self -->|sigil → sigils| blend
    consensus -->|result → sigils| blend
    val_G__204(([0.4 0.6]))
    val_G__204 -->|weights| blend
    similarity -->|score → score| choose
    val_G__205((0.75))
    val_G__205 -->|threshold| choose
    ctx-self -->|sigil → above| choose
    blend -->|result → below| choose
```

---

## :xenotype-071: Work on the Decayed + Focus

**Family:** Difficulty | **Hexagram:** 18 | **Energy:** :ji

Difficulty with focus: when entropy is high (decay), focus mutation toward the modal sigil to restore order

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    neighbors([context-neighbors])
    entropy[entropy]
    mode[modal]
    focus-mutate[mutate-toward]
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| entropy
    neighbors -->|sigils → sigils| mode
    ctx-self -->|sigil → sigil| focus-mutate
    mode -->|result → target| focus-mutate
    val_G__206((0.4))
    val_G__206 -->|rate| focus-mutate
    entropy -->|score → score| choose
    val_G__207((0.6))
    val_G__207 -->|threshold| choose
    focus-mutate -->|result → above| choose
    ctx-self -->|sigil → below| choose
```

---

## :xenotype-104: Great Taming + Push

**Family:** Youthful | **Hexagram:** 26 | **Energy:** :an

Youthful learning with sustained push: explore through crossover with neighbors, accumulating experience

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    ctx-pred([context-pred])
    ctx-succ([context-succ])
    cross-pred[uniform-crossover]
    cross-succ[uniform-crossover]
    blend[blend]
    style blend stroke:#333,stroke-width:3px

    %% Edges
    ctx-self -->|sigil → a| cross-pred
    ctx-pred -->|sigil → b| cross-pred
    val_G__208((0.3))
    val_G__208 -->|rate| cross-pred
    ctx-self -->|sigil → a| cross-succ
    ctx-succ -->|sigil → b| cross-succ
    val_G__209((0.3))
    val_G__209 -->|rate| cross-succ
    cross-pred -->|result → sigils| blend
    cross-succ -->|result → sigils| blend
    val_G__210(([0.5 0.5]))
    val_G__210 -->|weights| blend
```

---

## :xenotype-129: Retreat + Expand

**Family:** Waiting | **Hexagram:** 33 | **Energy:** :peng

Patient waiting with expansion: hold position but prepare for action by expanding internal diversity

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    neighbors([context-neighbors])
    autocorr[autocorr]
    expand[mutate]
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| autocorr
    ctx-self -->|sigil → sigil| expand
    val_G__211((0.15))
    val_G__211 -->|rate| expand
    autocorr -->|score → score| choose
    val_G__212((0.5))
    val_G__212 -->|threshold| choose
    expand -->|result → above| choose
    ctx-self -->|sigil → below| choose
```

---

## :xenotype-166: Increase + Yield

**Family:** Conflict | **Hexagram:** 42 | **Energy:** :lu

Resolving tension through yielding: when neighbors conflict, yield toward the minority to restore balance

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    neighbors([context-neighbors])
    majority[majority]
    minority[minority]
    dominance[dominance]
    yield[mutate-toward]
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| majority
    neighbors -->|sigils → sigils| minority
    neighbors -->|sigils → sigils| dominance
    ctx-self -->|sigil → sigil| yield
    minority -->|result → target| yield
    val_G__213((0.25))
    val_G__213 -->|rate| yield
    dominance -->|score → score| choose
    val_G__214((0.6))
    val_G__214 -->|threshold| choose
    yield -->|result → above| choose
    ctx-self -->|sigil → below| choose
```

---

## :xenotype-199: The Cauldron + Focus

**Family:** Army | **Hexagram:** 50 | **Energy:** :ji

Army coordination with focus: identify repeating patterns and reinforce them through focused mutation

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    neighbors([context-neighbors])
    repeats[find-repeats]
    mode[modal]
    reinforce[mutate-toward]
    differentiate[bit-xor]
    ctx-pred([context-pred])
    ctx-succ([context-succ])
    choose{threshold-sigil}
    style choose stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| repeats
    neighbors -->|sigils → sigils| mode
    ctx-self -->|sigil → sigil| reinforce
    mode -->|result → target| reinforce
    val_G__215((0.5))
    val_G__215 -->|rate| reinforce
    ctx-pred -->|sigil → a| differentiate
    ctx-succ -->|sigil → b| differentiate
    repeats -->|count → score| choose
    val_G__216((0.5))
    val_G__216 -->|threshold| choose
    reinforce -->|result → above| choose
    differentiate -->|result → below| choose
```

---

## :xenotype-232: The Joyous + Push

**Family:** Joy | **Hexagram:** 58 | **Energy:** :an

Joy through sustained harmony: blend with neighbors to maximize evenness and shared patterns

```mermaid
graph LR

    %% Nodes
    ctx-self([context-self])
    ctx-pred([context-pred])
    ctx-succ([context-succ])
    neighbors([context-neighbors])
    evenness[evenness]
    harmony-blend[blend]
    joy-mutate[mutate]
    style joy-mutate stroke:#333,stroke-width:3px

    %% Edges
    neighbors -->|sigils → sigils| evenness
    ctx-pred -->|sigil → sigils| harmony-blend
    ctx-self -->|sigil → sigils| harmony-blend
    ctx-succ -->|sigil → sigils| harmony-blend
    val_G__217(([0.33 0.34 0.33]))
    val_G__217 -->|weights| harmony-blend
    harmony-blend -->|result → sigil| joy-mutate
    val_G__218((0.05))
    val_G__218 -->|rate| joy-mutate
```

---

# Prototype Scorer Wirings

These 4 prototypes demonstrate one scorer per energy type.

## :scorer-peng: Diversity Scorer

**Energy:** :peng (Expand)

Diversity-focused scorer: rewards spatial variety and change

```mermaid
graph LR

    %% Nodes
    input([run-frames])
    activity[activity-series]
    avg-activity[mean]
    var-activity[variance]
    lambda[lambda-estimate]
    activity-score[multiply]
    var-boost[log1p]
    combined[add]
    out([output-score])
    style out stroke:#333,stroke-width:3px

    %% Edges
    input -->|frames| activity
    activity -->|series| avg-activity
    activity -->|series| var-activity
    input -->|frames| lambda
    avg-activity -->|avg → a| activity-score
    lambda -->|lambda → b| activity-score
    var-activity -->|var → x| var-boost
    activity-score -->|result → a| combined
    var-boost -->|result → b| combined
    combined -->|result| out
```

---

## :scorer-lu: Stability Scorer

**Energy:** :lu (Yield)

Stability-focused scorer: rewards temporal persistence and coherence

```mermaid
graph LR

    %% Nodes
    input([run-frames])
    skeletons[thin-all]
    persist-series[persistence-series]
    avg-persist[mean]
    frame0[frame-at]
    skel0[thin-frame]
    giant[giant-component-frac]
    combined[multiply]
    out([output-score])
    style out stroke:#333,stroke-width:3px

    %% Edges
    input -->|frames| skeletons
    skeletons -->|skeletons| persist-series
    persist-series -->|series| avg-persist
    input -->|frames| frame0
    frame0 -->|frame| skel0
    skel0 -->|skeleton| giant
    avg-persist -->|avg → a| combined
    giant -->|frac → b| combined
    combined -->|result| out
```

---

## :scorer-ji: Edge-of-Chaos Scorer

**Energy:** :ji (Focus)

Edge-of-chaos scorer: rewards lambda near 0.5 (critical point)

```mermaid
graph LR

    %% Nodes
    input([run-frames])
    lambda[lambda-estimate]
    dist[eoc-distance]
    inv-dist[invert]
    entropy[entropy-score]
    combined[multiply]
    out([output-score])
    style out stroke:#333,stroke-width:3px

    %% Edges
    input -->|frames| lambda
    lambda -->|lambda| dist
    dist -->|dist → x| inv-dist
    input -->|frames| entropy
    inv-dist -->|result → a| combined
    entropy -->|score → b| combined
    combined -->|result| out
```

---

## :scorer-an: Filament Complexity Scorer

**Energy:** :an (Push)

Filament complexity scorer: rewards branching, cycles, and persistent structure

```mermaid
graph LR

    %% Nodes
    input([run-frames])
    skeletons[thin-all]
    frame-count[frames-count]
    last-frame[frame-at]
    last-skel[thin-frame]
    length[skeleton-length]
    branch[branchpoint-density]
    cycles[cycle-count]
    endpoint[endpoint-density]
    persist-series[persistence-series]
    avg-persist[mean]
    log-len[log1p]
    cycle-boost[add]
    endpoint-penalty[add]
    part1[multiply]
    part2[multiply]
    part3[multiply]
    final[divide]
    out([output-score])
    style out stroke:#333,stroke-width:3px

    %% Edges
    input -->|frames| skeletons
    input -->|frames| last-frame
    last-frame -->|frame| last-skel
    last-skel -->|skeleton| length
    last-skel -->|skeleton| branch
    last-skel -->|skeleton| cycles
    last-skel -->|skeleton| endpoint
    skeletons -->|skeletons| persist-series
    persist-series -->|series| avg-persist
    length -->|length → x| log-len
    cycles -->|cycles → a| cycle-boost
    endpoint -->|density → a| endpoint-penalty
    log-len -->|result → a| part1
    branch -->|density → b| part1
    part1 -->|result → a| part2
    cycle-boost -->|result → b| part2
    part2 -->|result → a| part3
    avg-persist -->|avg → b| part3
    part3 -->|result → a| final
    endpoint-penalty -->|result → b| final
    final -->|result| out
```

---

