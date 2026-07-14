# CyberAnts controlled statistical replay

Generated: `2026-07-14T14:03:24.129251194Z`
Pinned futon2 harness SHA: `73ec130a5befdc321ae1a04d51ef59d6bbabbc44`
Pinned `src/ants/compare.clj` blob: `412a87b7f14467a49f069b7b30d24a139179e5ee`
Pinned config blobs: `{:l5 "74ed076d51e776700e6593d216e1ae378f50f9a3", :random-wiring "fcd779cd8dc68f48abe3c0e167b7ff932c5245d2", :shuffled-parameter "8d220fb97d974cb043d87c04fa86896085fa85b1", :sigil-gradient "00667dfb0379422762052c077948f7613a971e24"}`
Protocol: 30 unseeded runs/arm/scenario, 300 ticks/run; deterministic control seed `20260714`.

Simulation rows are inherently stochastic. Intervals are two-sided 95% t intervals; starvation is the explicit share of score `0.0`.

## Arm summaries

| Scenario | Arm | Mean [95% CI] | Starvation |
|---|---|---:|---:|
| patchy | l5 | 12.297 [12.165, 12.428] | 0.000 (0/30) |
| patchy | sigil-gradient | 0.000 [0.000, 0.000] | 1.000 (30/30) |
| patchy | random-wiring | 12.343 [12.215, 12.471] | 0.000 (0/30) |
| patchy | shuffled-parameter | 12.227 [12.094, 12.359] | 0.000 (0/30) |
| sparse | l5 | 10.873 [10.741, 11.006] | 0.000 (0/30) |
| sparse | sigil-gradient | 0.000 [0.000, 0.000] | 1.000 (30/30) |
| sparse | random-wiring | 10.757 [10.629, 10.885] | 0.000 (0/30) |
| sparse | shuffled-parameter | 10.780 [10.650, 10.910] | 0.000 (0/30) |
| snowdrift | l5 | 121.435 [114.543, 128.326] | 0.000 (0/30) |
| snowdrift | sigil-gradient | 126.494 [122.429, 130.559] | 0.000 (0/30) |
| snowdrift | random-wiring | 122.884 [116.246, 129.521] | 0.000 (0/30) |
| snowdrift | shuffled-parameter | 129.243 [122.309, 136.178] | 0.000 (0/30) |

## Claims table

A claim is marked supported only when the independent-difference 95% CI excludes zero in the preregistered direction.

| Scenario | Comparison | Expected | Delta [95% CI] | Supported? |
|---|---|---|---:|---:|
| patchy | L5 - sigil-gradient | L5 > sigil | 12.297 [12.165, 12.428] | yes |
| patchy | L5 - random-wiring | L5 > control | -0.047 [-0.230, 0.137] | no |
| patchy | L5 - shuffled-parameter | L5 > control | 0.070 [-0.117, 0.257] | no |
| sparse | L5 - sigil-gradient | L5 > sigil | 10.873 [10.741, 11.006] | yes |
| sparse | L5 - random-wiring | L5 > control | 0.117 [-0.067, 0.301] | no |
| sparse | L5 - shuffled-parameter | L5 > control | 0.093 [-0.092, 0.279] | no |
| snowdrift | L5 - sigil-gradient | L5 < sigil | -5.059 [-13.060, 2.942] | no |
| snowdrift | L5 - random-wiring | L5 > control | -1.449 [-11.017, 8.119] | no |
| snowdrift | L5 - shuffled-parameter | L5 > control | -7.809 [-17.585, 1.968] | no |

## Interpretation constraint

The current futon2 external-config adapter applies numeric precision to live AIF state, while retaining policy, pattern-sense, and adaptation wiring mainly as provenance. The random-wiring control is therefore a valid structural permutation but may be operationally equivalent to L5 in this harness. The shuffled-parameter control includes a precision permutation and can be operationally distinct. Any null control result is evidence about this executed boundary, not evidence that arbitrary wiring is generally equivalent.
