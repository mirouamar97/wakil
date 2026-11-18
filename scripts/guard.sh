#!/usr/bin/env bash
# Guard for a PUBLIC repository: fails on secrets-like files, personal data patterns,
# and (for agent branches) on edits to red paths. Run locally: bash scripts/guard.sh
set -u
fail=0
say() { echo "GUARD: $*"; fail=1; }

cd "$(git rev-parse --show-toplevel)"
files=$(git ls-files)

# 1) Forbidden file names
bad=$(echo "$files" | grep -E '(^|/)(\.env(\..*)?|local\.properties|google-services\.json|.*\.(jks|keystore|p12|pfx|pem|key)|id_rsa.*|.*secrets?\.(json|ya?ml|properties))$' || true)
[ -n "$bad" ] && say "forbidden file(s) committed:" && echo "$bad"

# 2) Content patterns (text files only; this script and the gitleaks config are exempt)
scan() { # $1 = label, $2 = ERE
  hits=$(echo "$files" | grep -vE '^(scripts/guard\.sh|\.gitleaks\.toml|gradle/wrapper/)' \
    | xargs -r grep -InE -- "$2" 2>/dev/null || true)
  if [ -n "$hits" ]; then say "$1 found (values hidden):"; echo "$hits" | cut -d: -f1,2 | sort -u | head -20; fi
}
scan "private key block"      '-----BEGIN [A-Z ]*PRIVATE KEY-----'
scan "API-key-like string"    '(AIza[0-9A-Za-z_-]{35}|gsk_[0-9A-Za-z]{20,}|sk-[A-Za-z0-9_-]{20,}|ghp_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{30,}|xox[abp]-[0-9A-Za-z-]{10,}|[0-9]{8,10}:[A-Za-z0-9_-]{35})'
scan "phone number"           '(\+|00)[0-9]{2,3}[ .-]?[0-9]([ .-]?[0-9]){7,11}|(^|[^0-9A-Za-z])0[567]([ .-]?[0-9]{2}){4}([^0-9]|$)'
# e-mail: allow noreply / example.* / placeholder domains
mailhits=$(echo "$files" | grep -vE '^(scripts/guard\.sh|\.gitleaks\.toml|gradle/wrapper/)' \
  | xargs -r grep -InoE -- '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}' 2>/dev/null \
  | grep -viE '@(example\.(com|org|net)|users\.noreply\.github\.com|noreply\.github\.com|anthropic\.com)$' \
  | grep -viE 'noreply@' || true)
[ -n "$mailhits" ] && say "e-mail address found (values hidden):" && echo "$mailhits" | cut -d: -f1,2 | sort -u | head -20

# 3) Red paths: agent branches may not touch them
red='^(\.github/workflows/guard\.yml|scripts/guard\.sh|\.github/CODEOWNERS|\.gitleaks\.toml|app/src/main/java/app/wakil/agent/permissions/|.*\.jks|.*\.keystore)'
head_ref="${GITHUB_HEAD_REF:-$(git rev-parse --abbrev-ref HEAD)}"
base_ref="${GITHUB_BASE_REF:-main}"
case "$head_ref" in
  evolve/*|agent/*)
    git fetch -q origin "$base_ref" 2>/dev/null || true
    changed=$(git diff --name-only "origin/$base_ref"...HEAD 2>/dev/null || true)
    touched=$(echo "$changed" | grep -E "$red" || true)
    [ -n "$touched" ] && say "agent branch '$head_ref' touches red paths:" && echo "$touched"
    ;;
esac

if [ "$fail" -ne 0 ]; then echo "GUARD: FAILED"; exit 1; fi
echo "GUARD: ok"
