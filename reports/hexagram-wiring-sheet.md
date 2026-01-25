# Hexagram Wiring Test Sheet

*Generated: 2026-01-25T23:47:03.987472405*

All 64 I Ching hexagrams mapped to wiring diagrams and executed.

![Hexagram Grid](images/hexagram-wiring-grid.png)

---

## Entropy Extremes

### Highest Entropy (Most Chaotic)

These hexagrams produce complex, information-rich patterns:

![Hexagram 10](images/hex-10.png)

![Hexagram 5](images/hex-05.png)

![Hexagram 25](images/hex-25.png)

![Hexagram 59](images/hex-59.png)

![Hexagram 37](images/hex-37.png)

### Lowest Entropy (Most Ordered)

These hexagrams collapse to uniform or simple patterns:

![Hexagram 62](images/hex-62.png)

![Hexagram 7](images/hex-07.png)

![Hexagram 20](images/hex-20.png)

![Hexagram 4](images/hex-04.png)

![Hexagram 50](images/hex-50.png)

---

## Pattern Quality Assessment

**Passes:** 41 / 64 (64%)

**Fails:** 23 / 64
- BARCODE (horizontal stripes): 20
- CANDYCANE (diagonal stripes): 23

### Passing Hexagrams

- **#25 無妄** `slf:bit-xor→bit-or` (E:0.99, U:72)
- **#5 需** `slf:bit-xor→bit-xor` (E:0.99, U:72)
- **#10 履** `slf:bit-xor→bit-nor` (E:0.99, U:72)
- **#59 渙** `suc:bit-or→bit-xor` (E:0.99, U:71)
- **#48 井** `slf:bit-and→bit-xor` (E:0.99, U:70)
- **#44 姤** `slf:bit-nand→bit-xor` (E:0.99, U:70)
- **#57 巽** `slf:bit-or→bit-xor` (E:0.99, U:70)
- **#37 家人** `pre:bit-nand→bit-xor` (E:0.99, U:70)
- **#18 蠱** `pre:bit-or→bit-xor` (E:0.99, U:70)
- **#3 屯** `nei:bit-xor→bit-xor` (E:0.99, U:71)
- **#47 困** `suc:bit-xor→bit-xor` (E:0.99, U:71)
- **#63 既濟** `pre:bit-xor→bit-xor` (E:0.99, U:71)
- **#29 坎** `suc:bit-and→bit-xor` (E:0.99, U:67)
- **#28 大過** `suc:bit-nand→bit-xor` (E:0.99, U:67)
- **#39 蹇** `pre:bit-and→bit-xor` (E:0.99, U:69)
- **#46 升** `nei:bit-or→bit-xor` (E:0.99, U:65)
- **#27 頤** `nei:bit-nand→bit-and` (E:0.98, U:63)
- **#8 比** `nei:bit-and→bit-xor` (E:0.98, U:63)
- **#15 謙** `nei:bit-or→bit-and` (E:0.98, U:63)
- **#31 咸** `suc:bit-nand→bit-and` (E:0.98, U:63)
- **#42 益** `nei:bit-nand→bit-xor` (E:0.98, U:60)
- **#17 隨** `nei:bit-xor→bit-nor` (E:0.98, U:57)
- **#24 復** `nei:bit-and→bit-or` (E:0.97, U:59)
- **#61 中孚** `slf:bit-and→bit-nor` (E:0.97, U:59)
- **#9 小畜** `slf:bit-or→bit-nor` (E:0.97, U:59)
- **#58 兌** `suc:bit-xor→bit-nor` (E:0.97, U:55)
- **#19 臨** `nei:bit-and→bit-nor` (E:0.97, U:57)
- **#41 損** `pre:bit-and→bit-nor` (E:0.97, U:54)
- **#60 節** `suc:bit-and→bit-nor` (E:0.97, U:48)
- **#49 革** `pre:bit-xor→bit-nor` (E:0.95, U:44)
- **#14 大有** `slf:bit-nand→bit-or` (E:0.95, U:48)
- **#1 乾** `slf:bit-nand→bit-nor` (E:0.95, U:48)
- **#6 訟** `suc:bit-or→bit-nor` (E:0.93, U:41)
- **#12 否** `nei:bit-or→bit-nor` (E:0.93, U:41)
- **#26 大畜** `pre:bit-or→bit-nor` (E:0.93, U:41)
- **#33 遯** `slf:bit-nand→bit-and` (E:0.92, U:43)
- **#54 歸妹** `suc:bit-xor→bit-or` (E:0.86, U:34)
- **#51 震** `nei:bit-xor→bit-or` (E:0.84, U:30)
- **#38 睽** `suc:bit-nand→bit-or` (E:0.81, U:25)
- **#16 豫** `nei:bit-xor→bit-and` (E:0.74, U:17)
- **#55 豐** `nei:bit-nand→bit-or` (E:0.73, U:22)

---

## Full Summary

| # | Name | Lines | Formula | Entropy | Unique | Status |
|---|------|-------|---------|---------|--------|--------|
| 1 | 乾 | ⚊⚊⚊⚊⚊⚊ | `slf:bit-nand→bit-nor` | 0.95 | 48 | PASS |
| 2 | 坤 | ⚋⚋⚋⚋⚋⚋ | `nei:bit-and→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 3 | 屯 | ⚋⚋⚊⚋⚊⚋ | `nei:bit-xor→bit-xor` | 0.99 | 71 | PASS |
| 4 | 蒙 | ⚋⚊⚋⚊⚋⚋ | `suc:bit-or→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 5 | 需 | ⚊⚊⚊⚋⚊⚋ | `slf:bit-xor→bit-xor` | 0.99 | 72 | PASS |
| 6 | 訟 | ⚋⚊⚋⚊⚊⚊ | `suc:bit-or→bit-nor` | 0.93 | 41 | PASS |
| 7 | 師 | ⚋⚊⚋⚋⚋⚋ | `suc:bit-and→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 8 | 比 | ⚋⚋⚋⚋⚊⚋ | `nei:bit-and→bit-xor` | 0.98 | 63 | PASS |
| 9 | 小畜 | ⚊⚊⚋⚊⚊⚊ | `slf:bit-or→bit-nor` | 0.97 | 59 | PASS |
| 10 | 履 | ⚊⚊⚊⚋⚊⚊ | `slf:bit-xor→bit-nor` | 0.99 | 72 | PASS |
| 11 | 泰 | ⚊⚊⚊⚋⚋⚋ | `slf:bit-xor→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 12 | 否 | ⚋⚋⚋⚊⚊⚊ | `nei:bit-or→bit-nor` | 0.93 | 41 | PASS |
| 13 | 同人 | ⚊⚋⚊⚊⚊⚊ | `pre:bit-nand→bit-nor` | -0.00 | 1 | FAIL: barcode,candycane |
| 14 | 大有 | ⚊⚊⚊⚊⚋⚊ | `slf:bit-nand→bit-or` | 0.95 | 48 | PASS |
| 15 | 謙 | ⚋⚋⚋⚊⚋⚋ | `nei:bit-or→bit-and` | 0.98 | 63 | PASS |
| 16 | 豫 | ⚋⚋⚊⚋⚋⚋ | `nei:bit-xor→bit-and` | 0.74 | 17 | PASS |
| 17 | 隨 | ⚋⚋⚊⚋⚊⚊ | `nei:bit-xor→bit-nor` | 0.98 | 57 | PASS |
| 18 | 蠱 | ⚊⚋⚋⚊⚊⚋ | `pre:bit-or→bit-xor` | 0.99 | 70 | PASS |
| 19 | 臨 | ⚋⚋⚋⚋⚊⚊ | `nei:bit-and→bit-nor` | 0.97 | 57 | PASS |
| 20 | 觀 | ⚊⚊⚋⚋⚋⚋ | `slf:bit-and→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 21 | 噬嗑 | ⚊⚋⚊⚋⚋⚊ | `pre:bit-xor→bit-or` | 0.82 | 32 | FAIL: candycane |
| 22 | 賁 | ⚊⚋⚋⚊⚋⚊ | `pre:bit-or→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 23 | 剝 | ⚊⚋⚋⚋⚋⚋ | `pre:bit-and→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 24 | 復 | ⚋⚋⚋⚋⚋⚊ | `nei:bit-and→bit-or` | 0.97 | 59 | PASS |
| 25 | 無妄 | ⚊⚊⚊⚋⚋⚊ | `slf:bit-xor→bit-or` | 0.99 | 72 | PASS |
| 26 | 大畜 | ⚊⚋⚋⚊⚊⚊ | `pre:bit-or→bit-nor` | 0.93 | 41 | PASS |
| 27 | 頤 | ⚋⚋⚊⚊⚋⚋ | `nei:bit-nand→bit-and` | 0.98 | 63 | PASS |
| 28 | 大過 | ⚋⚊⚊⚊⚊⚋ | `suc:bit-nand→bit-xor` | 0.99 | 67 | PASS |
| 29 | 坎 | ⚋⚊⚋⚋⚊⚋ | `suc:bit-and→bit-xor` | 0.99 | 67 | PASS |
| 30 | 離 | ⚊⚋⚊⚊⚋⚊ | `pre:bit-nand→bit-or` | 0.76 | 26 | FAIL: candycane |
| 31 | 咸 | ⚋⚊⚊⚊⚋⚋ | `suc:bit-nand→bit-and` | 0.98 | 63 | PASS |
| 32 | 恆：亨，無咎，利貞，利有攸往。 | ⚊⚊⚋⚋⚋⚊ | `slf:bit-and→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 33 | 遯 | ⚊⚊⚊⚊⚋⚋ | `slf:bit-nand→bit-and` | 0.92 | 43 | PASS |
| 34 | 大壯 | ⚋⚋⚊⚊⚊⚊ | `nei:bit-nand→bit-nor` | -0.00 | 1 | FAIL: barcode,candycane |
| 35 | 晉 | ⚋⚋⚋⚊⚋⚊ | `nei:bit-or→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 36 | 明夷 | ⚊⚋⚊⚋⚋⚋ | `pre:bit-xor→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 37 | 家人 | ⚊⚋⚊⚊⚊⚋ | `pre:bit-nand→bit-xor` | 0.99 | 70 | PASS |
| 38 | 睽 | ⚋⚊⚊⚊⚋⚊ | `suc:bit-nand→bit-or` | 0.81 | 25 | PASS |
| 39 | 蹇 | ⚊⚋⚋⚋⚊⚋ | `pre:bit-and→bit-xor` | 0.99 | 69 | PASS |
| 40 | 解 | ⚋⚊⚋⚋⚋⚊ | `suc:bit-and→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 41 | 損 | ⚊⚋⚋⚋⚊⚊ | `pre:bit-and→bit-nor` | 0.97 | 54 | PASS |
| 42 | 益 | ⚋⚋⚊⚊⚊⚋ | `nei:bit-nand→bit-xor` | 0.98 | 60 | PASS |
| 43 | 夬 | ⚋⚊⚊⚊⚊⚊ | `suc:bit-nand→bit-nor` | -0.00 | 1 | FAIL: barcode,candycane |
| 44 | 姤 | ⚊⚊⚊⚊⚊⚋ | `slf:bit-nand→bit-xor` | 0.99 | 70 | PASS |
| 45 | 萃 | ⚋⚊⚊⚋⚋⚋ | `suc:bit-xor→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 46 | 升 | ⚋⚋⚋⚊⚊⚋ | `nei:bit-or→bit-xor` | 0.99 | 65 | PASS |
| 47 | 困 | ⚋⚊⚊⚋⚊⚋ | `suc:bit-xor→bit-xor` | 0.99 | 71 | PASS |
| 48 | 井 | ⚊⚊⚋⚋⚊⚋ | `slf:bit-and→bit-xor` | 0.99 | 70 | PASS |
| 49 | 革 | ⚊⚋⚊⚋⚊⚊ | `pre:bit-xor→bit-nor` | 0.95 | 44 | PASS |
| 50 | 鼎 | ⚊⚊⚋⚊⚋⚊ | `slf:bit-or→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 51 | 震 | ⚋⚋⚊⚋⚋⚊ | `nei:bit-xor→bit-or` | 0.84 | 30 | PASS |
| 52 | 艮 | ⚊⚋⚋⚊⚋⚋ | `pre:bit-or→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 53 | 漸 | ⚊⚊⚋⚊⚋⚋ | `slf:bit-or→bit-and` | -0.00 | 1 | FAIL: barcode,candycane |
| 54 | 歸妹 | ⚋⚊⚊⚋⚋⚊ | `suc:bit-xor→bit-or` | 0.86 | 34 | PASS |
| 55 | 豐 | ⚋⚋⚊⚊⚋⚊ | `nei:bit-nand→bit-or` | 0.73 | 22 | PASS |
| 56 | 旅 | ⚊⚋⚊⚊⚋⚋ | `pre:bit-nand→bit-and` | 0.99 | 69 | FAIL: candycane |
| 57 | 巽 | ⚊⚊⚋⚊⚊⚋ | `slf:bit-or→bit-xor` | 0.99 | 70 | PASS |
| 58 | 兌 | ⚋⚊⚊⚋⚊⚊ | `suc:bit-xor→bit-nor` | 0.97 | 55 | PASS |
| 59 | 渙 | ⚋⚊⚋⚊⚊⚋ | `suc:bit-or→bit-xor` | 0.99 | 71 | PASS |
| 60 | 節 | ⚋⚊⚋⚋⚊⚊ | `suc:bit-and→bit-nor` | 0.97 | 48 | PASS |
| 61 | 中孚 | ⚊⚊⚋⚋⚊⚊ | `slf:bit-and→bit-nor` | 0.97 | 59 | PASS |
| 62 | 小過 | ⚊⚋⚋⚋⚋⚊ | `pre:bit-and→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |
| 63 | 既濟 | ⚊⚋⚊⚋⚊⚋ | `pre:bit-xor→bit-xor` | 0.99 | 71 | PASS |
| 64 | 未濟 | ⚋⚊⚋⚊⚋⚊ | `suc:bit-or→bit-or` | -0.00 | 1 | FAIL: barcode,candycane |

## Methodology

Each hexagram's 6 lines determine wiring structure:

| Lines | Stage | Yang (⚊) | Yin (⚋) |
|-------|-------|----------|--------|
| 1-2 | Input | context-neighbors | context-self |
| 3-4 | Core | bit-xor | bit-and |
| 5-6 | Output | bit-xor | bit-or |

Line pairs select from 4 component variants (00, 01, 10, 11).
