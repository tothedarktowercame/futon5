# Mission 0 Technote 0: Exotic Programming Overview

This note defines "exotic programming" as used in Futon and summarizes the
core stack from xenotype to exotype to genotype to phenotype.

## What is exotic programming?

Exotic programming is a method for steering complex systems by writing
programs that manipulate the programs that generate behavior. Instead of
directly specifying the output, we shape the rules that generate outputs and
evaluate those rules with domain-aware criteria. In Futon, the method is
rooted in Cellular Automata (CA) and category-theoretic (CT) structure.

Exotic programming emphasizes:
- indirect control (programs about programs)
- transfer across domains (CA, ants, music, etc.)
- selection guided by evidence, not just aesthetics

## The xeno → exo → geno → pheno stack

The system is organized as a layered stack:

1. Xenotype (xeno)
   - The evaluation and selection logic.
   - Encodes what "good" looks like and how to choose among candidates.
   - In Mission 0, the xenotype is made MMCA-native and CT-expressible.

2. Exotype (exo)
   - The rewrite or perturbation program that operates on genotypes and
     phenotypes.
   - Examples include sigil-based rewrite rules and parameterized exotype
     operators.
   - Exotypes are the "active" control layer.

3. Genotype (geno)
   - The symbolic program that generates phenotype dynamics.
   - In MMCA, this is a sigil string that encodes the CA rule set.
   - Genotype structure determines the space of possible behaviors.

4. Phenotype (pheno)
   - The observable behavior of the system over time.
   - In MMCA, this is the CA trajectory (frames, motifs, patterns).
   - Phenotype is the primary target for edge-of-chaos evaluation.

## How the layers interact

- Xenotype selects or updates exotypes based on evidence from runs.
- Exotypes rewrite or perturb genotypes and/or phenotypes.
- Genotypes generate phenotypes over time.
- Phenotypes are scored and fed back to the xenotype as evidence.

This creates a closed loop:

  xenotype -> exotype -> genotype -> phenotype -> evaluation -> xenotype

## Why Mission 0 focuses on this loop

Mission 0 aims to make the loop self-sustaining and reproducible:
- Recognize edge-of-chaos (EoC) states programmatically.
- Use CT-native criteria to guide selection and tuning.
- Enable transfer so that improvements in MMCA yield reusable patterns in
  other domains (ants, music, etc.).

The outcome is not just "good runs" but a reusable method for finding and
maintaining EoC across domains.
