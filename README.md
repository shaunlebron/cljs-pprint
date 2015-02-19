# cljs-pprint

__CLJS needs a pretty printer__ - Clojure has two pretty-printers available, which are being ported to ClojureScript:

- [`clojure.pprint`](https://clojure.github.io/clojure/clojure.pprint-api.html) - (comprehensive printer) being ported to cljs here, required by [CLJS-710](http://dev.clojure.org/jira/browse/CLJS-710)
- [`fipp`](https://github.com/brandonbloom/fipp) - (lightweight & fast) being ported to cljs at [fipp issue 7](https://github.com/brandonbloom/fipp/issues/7).

# Overview

- `src/clj` and `test/clj` has the original clojure pprint
- `src/cljs`  and `test/cljs` has the in-progress clojurescript pprint
- `src/diff` generates pprint implementation diffs between clj and cljs
- `src/report` displays the diff report

[See docs for more notes](docs/)

# Running

```sh
# runs the diff tool
lein run
```
