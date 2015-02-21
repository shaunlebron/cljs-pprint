# Help bring __pretty printing__ to ClojureScript

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
library from Clojure to ClojureScript.

__Why this page?__

Porting clojure.pprint has been an unexpectedly large task.
I built this page to help track the differences between the original and ported
functions, as well as track the defs/functions left to be ported.  And to
hopefully get help!

__How can I help?__

You can use the progress map below to find functions to port, or use the
side-by-side section to help review the current ports.

__Alternatives?__

Not yet, but [fipp](https://github.com/brandonbloom/fipp) is a faster, more
idiomatic EDN printer being
[ported](https://github.com/brandonbloom/fipp/issues/7) to ClojureScript,
albeit it has less features than clojure.pprint. Also check out
[cljs-devtools](https://github.com/binaryage/cljs-devtools), an awesome work in
progress for inspecting ClojureScript data in the Chrome console. And
[pretty-print.net](https://github.com/comamitc/pretty-print.net) is another
work in progress that will allow you to pretty-print pasted EDN online.


