#!/usr/bin/env bash
set -euo pipefail

git config --local user.email "action@github.com"
git config --local user.name "GitHub Action"

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

cp -R npm/typedoc/. "$tmpdir/typedoc"
# staged before the branch switch, because the landing page only exists on the source branch
cp docs/index.html "$tmpdir/index.html"

# a first deploy has no gh-pages to branch from, so start an orphan rather than force-pushing
if git fetch origin gh-pages 2> /dev/null; then
	git branch --no-track gh-pages origin/gh-pages 2> /dev/null || true
	git switch -f gh-pages
else
	git switch --orphan gh-pages
	# --orphan keeps the index, so the source tree would be committed without this
	git rm -rq --cached . 2> /dev/null || true
	find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
fi

# only this tool's own subdirectory is cleared, so the sibling's output survives
rm -rf typedoc
cp -R "$tmpdir/typedoc" typedoc
cp "$tmpdir/index.html" index.html

# scoped rather than `git add -A`: the working tree still holds build output and node_modules,
# and gh-pages carries no .gitignore to keep them out
git add -A -- typedoc index.html

if git diff --cached --quiet; then
	echo "No TypeDoc changes to deploy."
	exit 0
fi

git commit -m "Update TypeDoc ($1)"
git push origin gh-pages
