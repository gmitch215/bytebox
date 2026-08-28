#!/usr/bin/env bash
set -euo pipefail

git config --local user.email "action@github.com"
git config --local user.name "GitHub Action"

tmpdir="$(mktemp -d)"
cp -R npm/typedoc/. "$tmpdir/"

# a first deploy has no gh-pages to branch from, so start an orphan rather than force-pushing
if git fetch origin gh-pages 2> /dev/null; then
	git branch --no-track gh-pages origin/gh-pages 2> /dev/null || true
	git switch -f gh-pages
else
	git switch --orphan gh-pages
	find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
fi

# only this tool's subdirectory is cleared, so the sibling's output survives
rm -rf typedoc
mkdir -p typedoc
cp -R "$tmpdir"/. typedoc/
rm -rf "$tmpdir"

if [ -f "$(git rev-parse --show-toplevel)/docs/index.html" ]; then
	git show "origin/${GITHUB_REF_NAME:-master}:docs/index.html" > index.html 2> /dev/null || true
fi

git add -A

if git diff --cached --quiet; then
	echo "No TypeDoc changes to deploy."
	exit 0
fi

git commit -m "Update TypeDoc ($1)"
git push origin gh-pages
