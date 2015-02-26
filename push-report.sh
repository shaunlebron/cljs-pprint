#!/bin/bash

set -e

cd `dirname $0`

if [ ! -d hosted ]; then
  git clone git@github.com:shaunlebron/cljs-pprint.git hosted
fi

cd hosted

# checkout gh-pages branch
git checkout gh-pages

# make sure gh-pages is up-to-date
git pull

# remove all files
git rm -rf .

# add new report files
lein cljsbuild once report-prod
cp -r ../resources/report/* .

# clean out unneeded
rm -rf js/out-prod \
       js/out \
       js/report.js

# choose production page
mv index_prod.html index.html

git add .
git commit -m "auto-update"

# publish to website
git push

