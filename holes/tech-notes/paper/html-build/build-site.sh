#!/usr/bin/env bash
# build-site.sh -- REQUIRES latexml-oxide. The Perl LaTeXML path is retired.
#
# Why (Joe, 2026-08-22): Perl LaTeXML 0.8.8 cannot process a current TeX Live
# preamble -- it dies inside expl3-code.tex (line ~36005, "Missing argument
# Match:?") on any document loading modern LaTeX3, which is all of them now.
# No binding or shim reaches that; it is a version skew, not a gap.
#
# latexml-oxide converts the same document with a ~25-line ordinary-LaTeX shim
# and no Perl bindings at all. Keeping a second path that silently produces
# nothing is the stale-surface failure this repo keeps finding elsewhere, so
# the Perl script is retired rather than left as a fallback.
#
# The retired script is preserved as build-site-perl.sh.deprecated for
# reference (it still documents the nine .ltxml bindings and why each existed).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$HERE/build-site-oxide.sh" "$@"
