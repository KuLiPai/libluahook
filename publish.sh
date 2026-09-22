#!/usr/bin/env bash
#
# Publish libluahook to JitPack.
#
# JitPack builds from Git tags, so releasing == pushing a tag.
# This script makes sure the tag and the version written in gradle.properties
# always agree, then pushes both the branch and the tag.
#
# Usage:
#   ./publish.sh 1.0.0-alpha03            # set version, commit, tag, push
#   ./publish.sh 1.0.0-alpha03 -m "msg"   # custom commit message
#   ./publish.sh 1.0.0-alpha03 -n         # dry run: show what would happen
#
# After the push, JitPack builds automatically. Grab the artifacts with:
#   implementation("com.github.kulipai.libluahook:luahook-core:1.0.0-alpha03")

set -euo pipefail

cd "$(dirname "$0")"

VERSION=""
MESSAGE=""
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -m|--message) MESSAGE="${2:-}"; shift 2 ;;
    -n|--dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "unknown option: $1" >&2; exit 2 ;;
    *) VERSION="$1"; shift ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }
run() {
  if [[ $DRY_RUN -eq 1 ]]; then
    echo "  [dry-run] $*"
  else
    "$@"
  fi
}

# --- validate version ---------------------------------------------------------

[[ -n "$VERSION" ]] || die "missing version. example: ./publish.sh 1.0.0-alpha03"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
  || die "'$VERSION' is not a valid semver version"
[[ "$VERSION" == *+* ]] && die "'$VERSION' contains '+', which JitPack rejects in tags"

# JitPack needs a tag name that it can map back to the groupId
# com.github.<user>.<repo>. Keep tags free of characters that break that mapping.
[[ "$VERSION" =~ ^[0-9A-Za-z._-]+$ ]] || die "'$VERSION' contains characters JitPack cannot handle"

# --- validate repo state ------------------------------------------------------

git rev-parse --git-dir >/dev/null 2>&1 || die "not a git repository"
git remote get-url origin >/dev/null 2>&1 || die "no 'origin' remote configured"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
[[ "$BRANCH" != "HEAD" ]] || die "detached HEAD; check out a branch first"

if git rev-parse -q --verify "refs/tags/$VERSION" >/dev/null; then
  die "tag '$VERSION' already exists (delete it first if you meant to re-release)"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "the working tree has uncommitted changes:"
  git status --short
  die "commit or stash them first so the tag matches what you tested"
fi

# --- check version consistency ------------------------------------------------

CURRENT="$(sed -n 's/^version=//p' gradle.properties | head -1)"
[[ -n "$CURRENT" ]] || die "no 'version=' line found in gradle.properties"

if [[ "$CURRENT" == "$VERSION" ]]; then
  echo "gradle.properties already at $VERSION; skipping the bump commit"
  NEED_COMMIT=0
else
  echo "version: $CURRENT -> $VERSION  (gradle.properties)"
  NEED_COMMIT=1
fi

# --- confirm ------------------------------------------------------------------

echo
echo "  branch : $BRANCH"
echo "  tag    : $VERSION"
echo "  remote : $(git remote get-url origin)"
echo
if [[ $DRY_RUN -eq 0 ]]; then
  read -r -p "push branch and tag to origin? [y/N] " reply
  [[ "$reply" =~ ^[Yy]$ ]] || { echo "aborted"; exit 1; }
fi

# --- do it --------------------------------------------------------------------

if [[ $NEED_COMMIT -eq 1 ]]; then
  [[ -z "$MESSAGE" ]] && MESSAGE="chore(release): $VERSION"
  # GNU sed first, then BSD sed, so this works on both Linux and macOS.
  if sed -i "s/^version=.*/version=$VERSION/" gradle.properties 2>/dev/null; then
    :
  else
    sed -i '' "s/^version=.*/version=$VERSION/" gradle.properties
  fi
  run git add gradle.properties
  run git commit -m "$MESSAGE"
fi

run git tag -a -m "$VERSION" "$VERSION"

run git push origin "$BRANCH"
run git push origin "$VERSION"

echo
echo "pushed tag $VERSION."
echo "JitPack will pick it up at: https://jitpack.io/#KuLiPai/libluahook"
echo
echo "consume it with:"
echo "  implementation(\"com.github.kulipai.libluahook:luahook-core:$VERSION\")"
