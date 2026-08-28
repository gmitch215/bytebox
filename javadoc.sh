#!/usr/bin/env bash
set -euo pipefail

git config --local user.email "action@github.com"
git config --local user.name "GitHub Action"

start="$(git symbolic-ref --quiet --short HEAD || git rev-parse HEAD)"
tmpdir="$(mktemp -d)"
# the sibling script runs next and needs its own sources back, so the branch is always restored
trap 'rm -rf "$tmpdir"; git switch -f -q "$start" 2> /dev/null || true' EXIT

cp -R build/docs/javadoc/. "$tmpdir/javadoc"
# staged before the branch switch, because the landing page only exists on the source branch
cp docs/index.html "$tmpdir/index.html"

# a first deploy has no gh-pages to branch from, so start an orphan rather than force-pushing
if git fetch origin gh-pages 2> /dev/null; then
	git branch --no-track -f gh-pages origin/gh-pages 2> /dev/null || true
	git switch -f gh-pages
else
	git switch --orphan gh-pages
	# --orphan keeps the index, and only the index needs clearing; the working tree is left alone
	# so the sibling script's build output survives
	git rm -rq --cached . 2> /dev/null || true
fi

# only this tool's own subdirectory is replaced, so the sibling's output survives
rm -rf javadoc
cp -R "$tmpdir/javadoc" javadoc
cp "$tmpdir/index.html" index.html

# scoped rather than `git add -A`: the working tree still holds source, build output and
# node_modules, and gh-pages carries no .gitignore to keep them out
git add -A -- javadoc index.html

if git diff --cached --quiet; then
	echo "No Javadoc changes to deploy."
	exit 0
fi

git commit -m "Update Javadoc ($1)"
git push origin gh-pages
