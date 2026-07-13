# TN-synthetic-lean-validation.result

**Date:** 2026-06-02
**Owner:** codex-3
**Input:** `TN-synthetic-lean-validation.md`, `resources/iiching-ct/iiching-ct-codebook.edn`, read-only declaration search under `~/code/mathlib4/Mathlib/CategoryTheory/`

## Summary

T1 is complete: all 64 iiching-CT generators were mapped against Mathlib declarations. I count 52 realized scopes and 12 cuts/non-core scopes under the conservative rule: realized means the generator names a canonical Mathlib construction usable as the Lean scope, not merely a comment, import, or adjacent metaphor.

T2 was skipped. `lake`/`elan` are present, but the local no-fetch build/cache path was not ready: a lightweight `lake env lean --version` probe attempted to download/install Lean `4.31.0-rc1` and fetch Lake packages. I killed that path and did not run a witness compile.

T3 is included as a small structural retract table over core declarations. It is descriptive: it shows how a Lean signature can retract to a generator from referenced types/symbols rather than identifier text.

## T1 Structural Realization Map

| code | generator | Mathlib declaration / construction | file | realized? |
|---|---|---|---|---|
| 000000 | image | `CategoryTheory.Limits.Image.imageFactorisation`, `Image.isImage` | `Mathlib/CategoryTheory/Limits/Shapes/Images.lean` | Y |
| 000001 | functor | `CategoryTheory.Functor` | `Mathlib/CategoryTheory/Functor/Basic.lean` | Y |
| 000010 | relation | no canonical CategoryTheory relation object; only local cover relations / meta relations | n/a | N |
| 000011 | bicategory | `CategoryTheory.Bicategory` | `Mathlib/CategoryTheory/Bicategory/Basic.lean` | Y |
| 000100 | grothendieck topos | no canonical `GrothendieckTopos` declaration/class | n/a | N |
| 000101 | cartesian closed category | cartesian closed instances/constructions for `Cat` and closed cartesian monoidal categories | `Mathlib/CategoryTheory/Category/Cat/CartesianClosed.lean`, `Mathlib/CategoryTheory/Monoidal/Closed/Cartesian.lean` | Y |
| 000110 | reflective subcategory | `CategoryTheory.Reflective`, `reflector`, `reflectorAdjunction` | `Mathlib/CategoryTheory/Adjunction/Reflective.lean` | Y |
| 000111 | topos | no canonical `Topos` declaration/class; only topos-related classifier/sheaf constructions | n/a | N |
| 001000 | adjunction | `CategoryTheory.Adjunction` | `Mathlib/CategoryTheory/Adjunction/Basic.lean` | Y |
| 001001 | enriched category | enriched category structures and enriched functors | `Mathlib/CategoryTheory/Enriched/Basic.lean` | Y |
| 001010 | cat | `CategoryTheory.Cat` | `Mathlib/CategoryTheory/Category/Cat.lean` | Y |
| 001011 | 2-category | `CategoryTheory.Bicategory.Strict` as strict 2-category structure | `Mathlib/CategoryTheory/Bicategory/Strict/Basic.lean` | Y |
| 001100 | geometric morphism | no canonical `GeometricMorphism` declaration found in `CategoryTheory` | n/a | N |
| 001101 | monad | `CategoryTheory.Monad` | `Mathlib/CategoryTheory/Monad/Basic.lean` | Y |
| 001110 | adjoint | `CategoryTheory.Adjunction`, notation `F ⊣ G` | `Mathlib/CategoryTheory/Adjunction/Basic.lean` | Y |
| 001111 | adjoint functor | `Functor.IsLeftAdjoint`, `Functor.IsRightAdjoint` / adjunction data | `Mathlib/CategoryTheory/Adjunction/Basic.lean` | Y |
| 010000 | pushout | `CategoryTheory.Limits.HasPushout`, `pushout` | `Mathlib/CategoryTheory/Limits/Shapes/Pullback/HasPullback.lean` | Y |
| 010001 | coproduct | `CategoryTheory.Limits.HasCoproduct`, coproduct notation/constructions | `Mathlib/CategoryTheory/Limits/Shapes/Products.lean` | Y |
| 010010 | pullback | `CategoryTheory.Limits.HasPullback`, `pullback` | `Mathlib/CategoryTheory/Limits/Shapes/Pullback/HasPullback.lean` | Y |
| 010011 | equivalence | `CategoryTheory.Equivalence`, `Functor.IsEquivalence` | `Mathlib/CategoryTheory/Equivalence.lean` | Y |
| 010100 | subobject | `CategoryTheory.Subobject` | `Mathlib/CategoryTheory/Subobject/Basic.lean` | Y |
| 010101 | tensor unit | monoidal unit object in `MonoidalCategory` | `Mathlib/CategoryTheory/Monoidal/Category.lean` | Y |
| 010110 | base change | pullback functors over comma/over categories | `Mathlib/CategoryTheory/Comma/Over/Pullback.lean` | Y |
| 010111 | model category | model-category material is not a canonical `CategoryTheory` scope here | n/a | N |
| 011000 | initial object | `CategoryTheory.Limits.HasInitial`, `IsInitial` | `Mathlib/CategoryTheory/Limits/Shapes/Terminal.lean` | Y |
| 011001 | forgetful functor | `forget`, `forget₂`, concrete/category-specific forgetful functors | `Mathlib/CategoryTheory/ConcreteCategory/Forget.lean` and many category-specific files | Y |
| 011010 | terminal object | `CategoryTheory.Limits.HasTerminal`, `IsTerminal` | `Mathlib/CategoryTheory/Limits/Shapes/Terminal.lean` | Y |
| 011011 | natural transformation | `CategoryTheory.NatTrans` | `Mathlib/CategoryTheory/NatTrans.lean` | Y |
| 011100 | colimit | `CategoryTheory.Limits.HasColimit`, `colimit` | `Mathlib/CategoryTheory/Limits/HasLimits.lean` | Y |
| 011101 | tensor product | monoidal tensor object / tensor notation in `MonoidalCategory` | `Mathlib/CategoryTheory/Monoidal/Category.lean` | Y |
| 011110 | quillen equivalence | no canonical `QuillenEquivalence` declaration in `CategoryTheory` | n/a | N |
| 011111 | quillen adjunction | no canonical `QuillenAdjunction` declaration in `CategoryTheory` | n/a | N |
| 100000 | subcategory | `FullSubcategory`, `WideSubcategory`, subobject-style full subcategories | `Mathlib/CategoryTheory/FullSubcategory.lean`, `Mathlib/CategoryTheory/WideSubcategory.lean` | Y |
| 100001 | category | `CategoryTheory.Category` | `Mathlib/CategoryTheory/Category/Basic.lean` | Y |
| 100010 | homotopy | no canonical CategoryTheory homotopy scope; only homotopy-category imports/usages through homological algebra | n/a | N |
| 100011 | groupoid | `CategoryTheory.Groupoid` | `Mathlib/CategoryTheory/Groupoid.lean` | Y |
| 100100 | grothendieck topology | `CategoryTheory.GrothendieckTopology` | `Mathlib/CategoryTheory/Sites/Grothendieck.lean` | Y |
| 100101 | span | `CategoryTheory.Span` | `Mathlib/CategoryTheory/Bicategory/Span/Basic.lean` | Y |
| 100110 | sheaf | `CategoryTheory.Sheaf`, `Presheaf.IsSheaf` | `Mathlib/CategoryTheory/Sites/Sheaf.lean` | Y |
| 100111 | kan complex | no canonical `KanComplex` declaration in `CategoryTheory` | n/a | N |
| 101000 | morphism | category homs, notation `X ⟶ Y`, `CategoryStruct.Hom` | `Mathlib/CategoryTheory/Category/Basic.lean` | Y |
| 101001 | opposite category | opposite categories and `Cᵒᵖ` machinery | `Mathlib/CategoryTheory/Opposites.lean` | Y |
| 101010 | small category | `CategoryTheory.SmallCategory` | `Mathlib/CategoryTheory/Category/Basic.lean` | Y |
| 101011 | type | category of types / `CategoryTheory.Types` machinery | `Mathlib/CategoryTheory/Types/Basic.lean` | Y |
| 101100 | presheaf | presheaves as `Cᵒᵖ ⥤ Type`; presheaf-specific constructions | `Mathlib/CategoryTheory/Topos/Sheaf.lean`, `Mathlib/CategoryTheory/Sites/Sheaf.lean` | Y |
| 101101 | monoidal category | `CategoryTheory.MonoidalCategory` | `Mathlib/CategoryTheory/Monoidal/Category.lean` | Y |
| 101110 | descent | `DescentData`, `DescentData'`, descent-as-coalgebra structures | `Mathlib/CategoryTheory/Sites/Descent/DescentData.lean`, `Mathlib/CategoryTheory/Sites/Descent/DescentDataPrime.lean` | Y |
| 101111 | yoneda embedding | `CategoryTheory.yoneda` | `Mathlib/CategoryTheory/Yoneda.lean` | Y |
| 110000 | kernel | `CategoryTheory.Limits.kernel`, `HasKernel` | `Mathlib/CategoryTheory/Limits/Shapes/Kernels.lean` | Y |
| 110001 | action | `CategoryTheory.ActionCategory`, `actionAsFunctor` | `Mathlib/CategoryTheory/Action.lean`, `Mathlib/CategoryTheory/Action/Basic.lean` | Y |
| 110010 | localization | `CategoryTheory.Localization`, `HasLocalization`, `Localization'` | `Mathlib/CategoryTheory/Localization/Construction.lean`, `Mathlib/CategoryTheory/Localization/HasLocalization.lean` | Y |
| 110011 | object | objects as elements of a type carrying `[Category C]` | `Mathlib/CategoryTheory/Category/Basic.lean` | Y |
| 110100 | abelian category | `CategoryTheory.Abelian` | `Mathlib/CategoryTheory/Abelian/Basic.lean` | Y |
| 110101 | braiding | `CategoryTheory.BraidedCategory` | `Mathlib/CategoryTheory/Monoidal/Braided/Basic.lean` | Y |
| 110110 | weak equivalence | no canonical weak-equivalence scope in `CategoryTheory`; only arbitrary `MorphismProperty` or model-category-adjacent imports | n/a | N |
| 110111 | homotopy category | homotopy categories exist in homological/algebraic-topology imports, not as a core `CategoryTheory` scope | n/a | N |
| 111000 | diagram | diagrams as functors `J ⥤ C`; also directed `Diagram` structures | `Mathlib/CategoryTheory/Functor/Basic.lean`, `Mathlib/CategoryTheory/Presentable/Directed.lean` | Y |
| 111001 | yoneda lemma | `yonedaEquiv`, `yonedaLemma` | `Mathlib/CategoryTheory/Yoneda.lean` | Y |
| 111010 | differential | `CategoryTheory.DifferentialObject` | `Mathlib/CategoryTheory/DifferentialObject.lean` | Y |
| 111011 | composition | category composition, notation `≫` | `Mathlib/CategoryTheory/Category/Basic.lean` | Y |
| 111100 | limit | `CategoryTheory.Limits.HasLimit`, `limit` | `Mathlib/CategoryTheory/Limits/HasLimits.lean` | Y |
| 111101 | representable functor | `RepresentableBy`, `CorepresentableBy` | `Mathlib/CategoryTheory/Yoneda.lean` | Y |
| 111110 | natural isomorphism | natural isomorphisms as isomorphisms in functor categories; `NatTrans` plus `Iso` | `Mathlib/CategoryTheory/NatTrans.lean`, `Mathlib/CategoryTheory/Functor/Category.lean` | Y |
| 111111 | stable homotopy category | no canonical stable homotopy category declaration in `CategoryTheory` | n/a | N |

## Soft-Entry Stress Test

### `relation`: cut

Verdict: **cut** as a generator-scope, unless the codebook explicitly weakens it to a local site/cover relation.

Evidence: searching `Mathlib/CategoryTheory` finds many uses of Lean's ambient `Relation.*` and several local `Relation` structures under sites/covers/hypercovers, but no canonical category-theoretic relation object matching the generator. `Subobject`, spans, and jointly monic spans are nearby categorical encodings, but they are not a Mathlib declaration named by this scope. Keeping `relation` would blur a primitive generator with implementation encodings.

### `differential`: keep

Verdict: **keep**.

Evidence: `Mathlib/CategoryTheory/DifferentialObject.lean` has a core `CategoryTheory.DifferentialObject` structure with differential-object morphisms and a forgetful functor. This is stronger than the borderline case in the task prompt: it is not only an `Algebra.Homology` object outside the CategoryTheory tree.

## T2 Witness Snippets

Skipped. A no-fetch local Mathlib build/cache was not available. The attempted lightweight probe triggered toolchain/package fetches before being killed, so I did not create or compile a witness Lean file. This result therefore relies on T1 structural declaration realization, as allowed by the task spec.

## T3 Structural Retract

This is the minimal non-lexical recognizer table: a Lean declaration can retract to a generator by the Mathlib symbols/types in its signature, not by prose or identifier tokens.

| observed Lean symbol/type in signature | retracts to generator |
|---|---|
| `[Category C]` | category |
| `X : C` under `[Category C]` | object |
| `X ⟶ Y` | morphism |
| `F : C ⥤ D` | functor |
| `α : F ⟶ G` where `F G : C ⥤ D` | natural transformation |
| `F ≅ G` in a functor category | natural isomorphism |
| `adj : F ⊣ G` or `Adjunction F G` | adjunction / adjoint functor |
| `T : Monad C` | monad |
| `[Limits.HasPullback f g]` / `pullback f g` | pullback |
| `[Limits.HasPushout f g]` / `pushout f g` | pushout |
| `[Limits.HasLimit F]` / `limit F` | limit |
| `[Limits.HasColimit F]` / `colimit F` | colimit |
| `yoneda.obj X` | yoneda embedding |
| `RepresentableBy F X` | representable functor |
| `J : GrothendieckTopology C` | grothendieck topology |
| `F : Sheaf J A` | sheaf |
| `[MonoidalCategory C]` | monoidal category |
| `[BraidedCategory C]` | braiding |
| `DifferentialObject S C` | differential |

This is enough to demonstrate the intended distinction: prose embeddings can miss "2-cell between functors", but a Lean signature containing `F ⟶ G` with `F G : C ⥤ D` structurally identifies `natural transformation`.

## Notes / Residue

The conservative cuts are not claims that Mathlib has no related material anywhere. In particular, model/homotopy material appears through `AlgebraicTopology` and `Algebra.Homology` imports used by some CategoryTheory files. They are cut here because this task asked for canonical `CategoryTheory` generator-scopes, and the table is meant to validate the 64 scopes as stable structural handles.

The generated report is descriptive only. It does not claim in-the-wild usage frequency and does not run Lean typechecking witnesses.
