#!/usr/bin/env bash
# Build and publish the front end to S3 + CloudFront.
#
# Run from bankui/:   ./deploy.sh
#
# TWO PASSES, and the order matters. The split is not "index.html versus the rest" - it is
# FINGERPRINTED versus NOT. Vite hashes everything under assets/, so index-CYlWWWOr.js can
# never mean two different things and is safe to cache forever. index.html and favicon.svg
# keep their names across deploys, so caching either one strands the site on an old version:
# a stale index.html points at bundles that no longer exist, and a stale favicon never
# updates at all.
#
# The headers are set at UPLOAD time because CloudFront only sends Cache-Control downstream
# if the origin object carries one. The cache policy governs the EDGE; this governs the
# BROWSER. Without it, browsers fall back to heuristic revalidation and re-fetch assets that
# were safe to keep.
set -euo pipefail

BUCKET=$(terraform -chdir=../terraform output -raw bucket_name)
DIST_ID=$(terraform -chdir=../terraform output -raw distribution_id)

npm run build

# 1. fingerprinted assets - cache hard. `immutable` goes further than a long max-age: it
#    tells the browser not to revalidate even on a manual refresh.
aws s3 sync dist/assets/ "s3://${BUCKET}/assets/" --delete \
  --cache-control "public, max-age=31536000, immutable"

# 2. everything else - must revalidate. `no-cache` does NOT mean "do not store"; it means
#    "ask whether it changed before reusing". A 304 costs one small round trip; a stale
#    index.html costs a broken deploy.
aws s3 sync dist/ "s3://${BUCKET}/" --delete \
  --exclude "assets/*" \
  --cache-control "no-cache"

#    GOTCHA: `sync` compares CONTENT, not metadata. An object whose bytes have not
#    changed is skipped even when the Cache-Control you ask for differs from what is
#    stored - so changing only a header here silently does nothing to files already
#    uploaded. index.html re-uploads every deploy because its asset hashes change; a
#    static favicon does not. To force a header onto an unchanged object, copy it onto
#    itself with --metadata-directive REPLACE, restating --content-type, because
#    REPLACE discards all existing metadata.

# 3. invalidate. Without this the edge keeps serving the previous objects until their TTL
#    expires - which for assets/ would be a year. The first 1000 paths a month are free.
#    MSYS_NO_PATHCONV stops Git Bash rewriting a leading-slash argument into a Windows
#    path. Without it, --paths "/index.html" reaches the CLI as
#    D:/Program Files/Git/index.html and fails with "invalid invalidation paths".
#    "/*" happens to survive; anything shaped like a real path does not.
MSYS_NO_PATHCONV=1 aws cloudfront create-invalidation \
  --distribution-id "${DIST_ID}" \
  --paths "/*" \
  --query "Invalidation.{Id:Id,Status:Status}" --output text
