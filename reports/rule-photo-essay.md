# Elementary CA Rules as MMCA Wirings

A visual exploration of classic cellular automaton rules implemented as wiring diagrams.

**Seed**: 352362012 | **Generations**: 80 | **Width**: 120

Each rule is implemented as a wiring diagram that computes the next cell state from the neighborhood.

---

## Rule 90 (Class 3)

**XOR(left, right) - Sierpinski triangle patterns, fractal structure**

Formula: `L XOR R`

### Wiring Diagram

```mermaid
flowchart TD
    pred[context-pred]
    succ[context-succ]
    xor-lr{bit-xor}
    output([output])
    pred -->|a| xor-lr
    succ -->|b| xor-lr
    xor-lr --> output
```

### Spacetime Diagrams

| Color (RGB from bits) | Grayscale (bit count) |
|:---------------------:|:---------------------:|
| ![Rule 90 color](images/rule-90-color.png) | ![Rule 90 gray](images/rule-90-gray.png) |

---

## Rule 110 (Class 4)

**Turing-complete, produces localized structures and 'gliders'**

Formula: `(C OR R) AND NOT(L AND C AND R)`

### Wiring Diagram

```mermaid
flowchart TD
    pred[context-pred]
    self[context-self]
    succ[context-succ]
    or-cr{bit-or}
    and-lc{bit-and}
    and-lcr{bit-and}
    not-lcr{bit-not}
    final{bit-and}
    output([output])
    self -->|a| or-cr
    succ -->|b| or-cr
    pred -->|a| and-lc
    self -->|b| and-lc
    and-lc -->|a| and-lcr
    succ -->|b| and-lcr
    and-lcr --> not-lcr
    or-cr -->|a| final
    not-lcr -->|b| final
    final --> output
```

### Spacetime Diagrams

| Color (RGB from bits) | Grayscale (bit count) |
|:---------------------:|:---------------------:|
| ![Rule 110 color](images/rule-110-color.png) | ![Rule 110 gray](images/rule-110-gray.png) |

---

## Rule 30 (Class 3)

**Chaotic, used for randomness generation in Mathematica**

Formula: `L XOR (C OR R)`

### Wiring Diagram

```mermaid
flowchart TD
    pred[context-pred]
    self[context-self]
    succ[context-succ]
    or-cr{bit-or}
    xor-l-or{bit-xor}
    output([output])
    self -->|a| or-cr
    succ -->|b| or-cr
    pred -->|a| xor-l-or
    or-cr -->|b| xor-l-or
    xor-l-or --> output
```

### Spacetime Diagrams

| Color (RGB from bits) | Grayscale (bit count) |
|:---------------------:|:---------------------:|
| ![Rule 30 color](images/rule-30-color.png) | ![Rule 30 gray](images/rule-30-gray.png) |

---

## Rule 184 (Class 2)

**Traffic/particle flow - conserves number of particles**

Formula: `(C AND R) OR (L AND NOT C)`

### Wiring Diagram

```mermaid
flowchart TD
    pred[context-pred]
    self[context-self]
    succ[context-succ]
    and-cr{bit-and}
    not-c{bit-not}
    and-l-notc{bit-and}
    or-final{bit-or}
    output([output])
    self -->|a| and-cr
    succ -->|b| and-cr
    self --> not-c
    pred -->|a| and-l-notc
    not-c -->|b| and-l-notc
    and-cr -->|a| or-final
    and-l-notc -->|b| or-final
    or-final --> output
```

### Spacetime Diagrams

| Color (RGB from bits) | Grayscale (bit count) |
|:---------------------:|:---------------------:|
| ![Rule 184 color](images/rule-184-color.png) | ![Rule 184 gray](images/rule-184-gray.png) |

---

## Rule 54 (Class 4)

**Complex periodic structures with localized patterns**

Formula: `(NOT L AND (C XOR R)) OR (L AND NOT C)`

### Wiring Diagram

```mermaid
flowchart TD
    pred[context-pred]
    self[context-self]
    succ[context-succ]
    not-l{bit-not}
    xor-cr{bit-xor}
    and-notl-xor{bit-and}
    not-c{bit-not}
    and-l-notc{bit-and}
    or-final{bit-or}
    output([output])
    pred --> not-l
    self -->|a| xor-cr
    succ -->|b| xor-cr
    not-l -->|a| and-notl-xor
    xor-cr -->|b| and-notl-xor
    self --> not-c
    pred -->|a| and-l-notc
    not-c -->|b| and-l-notc
    and-notl-xor -->|a| or-final
    and-l-notc -->|b| or-final
    or-final --> output
```

### Spacetime Diagrams

| Color (RGB from bits) | Grayscale (bit count) |
|:---------------------:|:---------------------:|
| ![Rule 54 color](images/rule-54-color.png) | ![Rule 54 gray](images/rule-54-gray.png) |

---

## Comparison

| Rule | Wolfram Class | Key Property | Formula |
|------|---------------|--------------|--------|
| 90 | 3 (Chaotic) | Sierpinski fractal | `L XOR R` |
| 110 | 4 (Complex) | Turing-complete | `(C OR R) AND NOT(L AND C AND R)` |
| 30 | 3 (Chaotic) | High entropy | `L XOR (C OR R)` |
| 184 | 2 (Periodic) | Particle conservation | `(C AND R) OR (L AND NOT C)` |
| 54 | 4 (Complex) | Localized structures | `(NOT L AND (C XOR R)) OR (L AND NOT C)` |

## Hexagram Connections

When these rules run on 8-bit sigils, the exotype sampling shows different hexagram distributions:

- **Rule 90** (XOR) produces alternating bitplane patterns mapping to hexagrams 63/64 (既濟/未濟)
- **Rule 184** (traffic) shows highest affinity for hexagram 11 (泰 - Peace)
- The 8-bit sigil space creates richer dynamics than binary CAs

## Technical Notes

- **Color images**: RGB derived from 8 sigil bits (bits 0-2 → R, bits 3-5 → G, bits 6-7 → B)
- **Grayscale images**: Brightness proportional to number of 1-bits in sigil
- Each wiring implements the rule's boolean formula using `bit-xor`, `bit-and`, `bit-or`, `bit-not` components
