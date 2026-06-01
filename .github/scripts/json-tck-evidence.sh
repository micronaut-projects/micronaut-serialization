#!/usr/bin/env bash
set -euo pipefail

suite="${1:?Usage: $0 <jsonp|jsonb|json> <results-dir> <output-dir>}"
results_dir="${2:?Usage: $0 <jsonp|jsonb|json> <results-dir> <output-dir>}"
output_dir="${3:?Usage: $0 <jsonp|jsonb|json> <results-dir> <output-dir>}"

mkdir -p "${output_dir}"

summary="${output_dir}/${suite}-summary.md"
index="${output_dir}/index.html"
sha_file="${output_dir}/${suite}-sha256.txt"
sanitized_dir="${output_dir}/junit-xml"
artifact="${output_dir}/${suite}-evidence.tar.gz"
artifact_sha="${artifact}.sha256"
case "${suite}" in
  jsonp) display_suite="JSON-P" ;;
  jsonb) display_suite="JSON-B" ;;
  json) display_suite="JSON" ;;
  *) display_suite="${suite}" ;;
esac

rm -rf "${sanitized_dir}"
mkdir -p "${sanitized_dir}"

if [[ -d "${results_dir}" ]]; then
  while IFS= read -r -d '' file; do
    relative="${file#${results_dir}/}"
    target="${sanitized_dir}/${relative}"
    mkdir -p "$(dirname "${target}")"
    sed \
      -e 's/ timestamp="[^"]*"/ timestamp="SANITIZED"/g' \
      -e 's/ hostname="[^"]*"/ hostname="SANITIZED"/g' \
      -e 's/ time="[^"]*"/ time="0"/g' \
      "${file}" > "${target}"
  done < <(find "${results_dir}" -name '*.xml' -type f -print0 | sort -z)
fi

tests="$(grep -ho 'tests="[0-9]*"' "${sanitized_dir}"/*.xml 2>/dev/null | cut -d\" -f2 | awk '{sum += $1} END {print sum + 0}')"
failures="$(grep -ho 'failures="[0-9]*"' "${sanitized_dir}"/*.xml 2>/dev/null | cut -d\" -f2 | awk '{sum += $1} END {print sum + 0}')"
errors="$(grep -ho 'errors="[0-9]*"' "${sanitized_dir}"/*.xml 2>/dev/null | cut -d\" -f2 | awk '{sum += $1} END {print sum + 0}')"
skipped="$(grep -ho 'skipped="[0-9]*"' "${sanitized_dir}"/*.xml 2>/dev/null | cut -d\" -f2 | awk '{sum += $1} END {print sum + 0}')"

workflow_url="not available"
if [[ -n "${GITHUB_SERVER_URL:-}" && -n "${GITHUB_REPOSITORY:-}" && -n "${GITHUB_RUN_ID:-}" ]]; then
  workflow_url="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
fi

{
  echo "# Jakarta ${display_suite} TCK Evidence"
  echo
  echo "- Commit: $(git rev-parse HEAD)"
  echo "- Project version: $(grep '^projectVersion=' gradle.properties | cut -d= -f2-)"
  echo "- JSON-P API baseline: $(awk -F\" '/^managed-jakarta-json-api = "/ {print $2; exit}' gradle/libs.versions.toml)"
  echo "- JSON-B API baseline: $(awk -F\" '/^managed-jakarta-json-bindApi = "/ {print $2; exit}' gradle/libs.versions.toml)"
  echo "- JSON-P TCK: $(awk -F\" '/^managed-jakarta-json-tck = "/ {print $2; exit}' gradle/libs.versions.toml)"
  echo "- JSON-B TCK: $(awk -F\" '/^managed-jakarta-json-bind-tck = "/ {print $2; exit}' gradle/libs.versions.toml)"
  echo "- Workflow: ${workflow_url}"
  echo "- Sanitized JUnit XML: junit-xml/"
  echo "- Tests: ${tests}"
  echo "- Failures: ${failures}"
  echo "- Errors: ${errors}"
  echo "- Skipped: ${skipped}"
} > "${summary}"

find "${sanitized_dir}" -name '*.xml' -type f -print0 | sort -z | xargs -0 shasum -a 256 > "${sha_file}" || true

{
  echo "<!doctype html>"
  echo "<html><head><meta charset=\"utf-8\"><title>Jakarta ${display_suite} TCK Evidence</title></head><body>"
  echo "<h1>Jakarta ${display_suite} TCK Evidence</h1>"
  echo "<pre>"
  sed 's/&/\&amp;/g; s/</\&lt;/g' "${summary}"
  echo "</pre>"
  echo "<ul>"
  echo "<li><a href=\"$(basename "${summary}")\">Summary Markdown</a></li>"
  echo "<li><a href=\"$(basename "${sha_file}")\">Sanitized XML SHA-256</a></li>"
  echo "<li><a href=\"junit-xml/\">Sanitized JUnit XML</a></li>"
  echo "</ul>"
  echo "</body></html>"
} > "${index}"

tar -C "${output_dir}" -czf "${artifact}" "junit-xml" "$(basename "${summary}")" "$(basename "${sha_file}")" "$(basename "${index}")"
shasum -a 256 "${artifact}" > "${artifact_sha}"
