# ClojureScript needs a __pretty printer!__

&lt;<http://github.com/shaunlebron/cljs-pprint>&gt;

```clojure
;; ugly
user=> data
{:hello "world", :things {:vegetables #{"cauliflower" "sprouts" "cucumber"}, :primes [2 3 5 7 11
13 17 19 23], :fruits #{"apple" "banana" "strawberry" "kiwi"}}}

;; pretty!
user=> (pprint data)
{:hello "world",
 :things
 {:vegetables #{"cauliflower" "sprouts" "cucumber"},
  :primes [2 3 5 7 11 13 17 19 23],
  :fruits #{"apple" "banana" "strawberry" "kiwi"}}}
```

__Why?__

Data is at the core of any ClojureScript application. We should be able to see
it clearly!

__How?__

We are porting the
[clojure.pprint](https://clojure.github.io/clojure/clojure.pprint-api.html)
library from Clojure to ClojureScript.  ([official tracker](http://dev.clojure.org/jira/browse/CLJS-710))

__Why this page?__

Porting clojure.pprint has been an unexpectedly large task.  I built this page
to help track the differences between the original and ported functions, as
well as track the defs/functions left to be ported.  And to make it easier to
involve the community.

__How can I help?__

You can use the progress map below to find functions to port, or use the
side-by-side section to help review the current ports.

__Alternative/Related projects?__

- [pretty-print.net](http://pretty-print.net) an online Clojure/EDN pretty-printer
- [fipp](https://github.com/brandonbloom/fipp) is a faster, more idiomatic, less featured EDN
  printer being [ported](https://github.com/brandonbloom/fipp/issues/7) to
  ClojureScript.
- [cljs-devtools](https://github.com/binaryage/cljs-devtools) is
  a work in progress for inspecting ClojureScript data in the Chrome
  console.
- [ankha](https://github.com/noprompt/ankha) is a data inspection component for
  Om that allows you view and edit data.



