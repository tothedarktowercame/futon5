# Elementary CA Rules as Wiring Diagrams

*Generated: 2026-01-25T15:50:23.840945700*

This essay visualizes elementary cellular automaton rules implemented as
wiring diagrams in the futon5 xenotype system. Each rule is executed through
the `futon5.wiring.runtime` interpreter, producing spacetime diagrams that
show the evolution of 8-bit sigil states.

---

## Overview

| Rule | Wolfram Class | Formula | Description |
|------|---------------|---------|-------------|
| Rule 90 | 3 | `L ⊕ R` | XOR(L,R) - Sierpinski triangle, additive |
| Rule 110 | 4 | `(C ∨ R) ∧ ¬(L ∧ C ∧ R)` | Turing-complete, localized structures |
| Rule 30 | 3 | `L ⊕ (C ∨ R)` | L XOR (C OR R) - chaotic, used for randomness |
| Rule 184 | 2 | `(C ∧ R) ∨ (L ∧ ¬C)` | Traffic flow, particle conservation |
| Rule 54 | 4 | `(¬L ∧ (C ⊕ R)) ∨ (L ∧ ¬C)` | Complex periodic structures |

## Rule 90

**Wolfram Class 3**: XOR(L,R) - Sierpinski triangle, additive

### Wiring Diagram

```mermaid
flowchart LR
    L["L (pred)"] --> XOR{XOR}
    R["R (succ)"] --> XOR
    XOR --> OUT([output])
```

### Run Statistics

- **Generations**: 101
- **Width**: 150 cells
- **Seed**: 352362012
- **Dynamics**: Entropy: 0.98 → 0.98 | Unique sigils: 111 → 111

### Spacetime Diagrams

| Color (8-bit) | Grayscale | Bitplane 0 |
|---------------|-----------|------------|
| ![Rule 90 color](images/rule-090-wiring-color.png) | ![Rule 90 gray](images/rule-090-wiring-gray.png) | ![Rule 90 bit0](images/rule-090-wiring-bit0.png) |

---

## Rule 110

**Wolfram Class 4**: Turing-complete, localized structures

### Wiring Diagram

```mermaid
flowchart LR
    L["L"] --> AND1{AND}
    C["C"] --> AND1
    C --> OR{OR}
    R["R"] --> OR
    AND1 --> AND2{AND}
    R --> AND2
    AND2 --> NOT{NOT}
    OR --> FINAL{AND}
    NOT --> FINAL
    FINAL --> OUT([output])
```

### Run Statistics

- **Generations**: 101
- **Width**: 150 cells
- **Seed**: 352362012
- **Dynamics**: Entropy: 0.98 → 0.98 | Unique sigils: 111 → 116

### Spacetime Diagrams

| Color (8-bit) | Grayscale | Bitplane 0 |
|---------------|-----------|------------|
| ![Rule 110 color](images/rule-110-wiring-color.png) | ![Rule 110 gray](images/rule-110-wiring-gray.png) | ![Rule 110 bit0](images/rule-110-wiring-bit0.png) |

---

## Rule 30

**Wolfram Class 3**: L XOR (C OR R) - chaotic, used for randomness

### Wiring Diagram

```mermaid
flowchart LR
    L["L"] --> XOR{XOR}
    C["C"] --> OR{OR}
    R["R"] --> OR
    OR --> XOR
    XOR --> OUT([output])
```

### Run Statistics

- **Generations**: 101
- **Width**: 150 cells
- **Seed**: 352362012
- **Dynamics**: Entropy: 0.98 → 0.98 | Unique sigils: 111 → 113

### Spacetime Diagrams

| Color (8-bit) | Grayscale | Bitplane 0 |
|---------------|-----------|------------|
| ![Rule 30 color](images/rule-030-wiring-color.png) | ![Rule 30 gray](images/rule-030-wiring-gray.png) | ![Rule 30 bit0](images/rule-030-wiring-bit0.png) |

---

## Rule 184

**Wolfram Class 2**: Traffic flow, particle conservation

### Wiring Diagram

```mermaid
flowchart LR
    L["L"] --> AND2{AND}
    C["C"] --> AND1{AND}
    C --> NOT{NOT}
    R["R"] --> AND1
    NOT --> AND2
    AND1 --> OR{OR}
    AND2 --> OR
    OR --> OUT([output])
```

### Run Statistics

- **Generations**: 101
- **Width**: 150 cells
- **Seed**: 352362012
- **Dynamics**: Entropy: 0.98 → 0.89 | Unique sigils: 111 → 24

### Spacetime Diagrams

| Color (8-bit) | Grayscale | Bitplane 0 |
|---------------|-----------|------------|
| ![Rule 184 color](images/rule-184-wiring-color.png) | ![Rule 184 gray](images/rule-184-wiring-gray.png) | ![Rule 184 bit0](images/rule-184-wiring-bit0.png) |

---

## Rule 54

**Wolfram Class 4**: Complex periodic structures

### Wiring Diagram

```mermaid
flowchart LR
    L["L"] --> NOT1{NOT}
    C["C"] --> XOR{XOR}
    R["R"] --> XOR
    NOT1 --> AND1{AND}
    XOR --> AND1
    C --> NOT2{NOT}
    L --> AND2{AND}
    NOT2 --> AND2
    AND1 --> OR{OR}
    AND2 --> OR
    OR --> OUT([output])
```

### Run Statistics

- **Generations**: 101
- **Width**: 150 cells
- **Seed**: 352362012
- **Dynamics**: Entropy: 0.98 → 0.98 | Unique sigils: 111 → 101

### Spacetime Diagrams

| Color (8-bit) | Grayscale | Bitplane 0 |
|---------------|-----------|------------|
| ![Rule 54 color](images/rule-054-wiring-color.png) | ![Rule 54 gray](images/rule-054-wiring-gray.png) | ![Rule 54 bit0](images/rule-054-wiring-bit0.png) |

---

## Methodology

Each rule is implemented as a wiring diagram with these components:

- **Context extractors**: `:context-pred`, `:context-self`, `:context-succ`
- **Boolean operations**: `:bit-xor`, `:bit-and`, `:bit-or`, `:bit-not`
- **Output**: `:output-sigil`

The wiring interpreter (`futon5.xenotype.interpret/evaluate-diagram`) executes
the diagram in topological order for each cell at each generation.

**Note**: These are 8-bit sigil CAs, not binary CAs. Each sigil represents
256 possible states. The boolean operations work bitwise across all 8 bits,
producing richer dynamics than traditional binary CAs.

## Files

- **Wiring definitions**: `data/wiring-rules/rule-*.edn`
- **Runtime**: `src/futon5/wiring/runtime.clj`
- **This script**: `scripts/wiring_photo_essay.clj`
- **Images**: `reports/images/*-wiring-*.png`
