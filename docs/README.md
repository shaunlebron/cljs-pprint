# Docs

Writing notes and plans here.

## diff plan

Creating a diff aid to easily show changes between pprint in clj and cljs.
Should help debugging and review:

- use clojure.tools.reader/analyzer to get text of each function/macro/var
- build index of functions/macros implemented

- dump each function (start to end lines) to a separate file
- run wdiff with the (-w -x -y -z args set to span tags with classes)
- output wdiff result to a result html file, with newlines replaced with br
- create result page (anchor links for each tag, and ToC)

# Thoughts

## pprint function changes

The original `pprint` requires an output `java.io.Writer` writer as its second arg, which
defaults to `*out*`.  `*out*` is unimplemented in ClojureScript. The [print section in cljs.core](https://github.com/shaunlebron/cljs-pprint/blob/master/src/cljs/cljs/core.cljs#L7701)
shows the print functions use an IWriter interface.  I followed the patterns of the different print
functions to create a basis for pprint usage:

- __`pprint`__ pretty-prints to `*print-fn*`
- __`pprint-str`__ pretty-prints to a string
- __`pprint-sb`__ pretty-prints to a `goog.StringBuffer`
- __`pprint*`__ pretty-prints to a given `IWriter`

## macro file

In order to port the macros, I have to create a separate `pprint.clj` file to hold them.

## pretty-writer changes

The pretty-writer constructor creates a `proxy` object that can be deref'd to
access a `ref` collection of fields.  I could port this to `reify` and `atom`,
respectively, but I need to take a look around to see why these are needed.  I
feel compelled to evaluate whether a `deftype` is more suitable here because it
looks it provides mutable fields with protocol implementation.

## Char->Int casting?

Not sure why the casting of `\newline` is done in `c-write-char` but not in
`p-write-char`.  Makes me wonder why ints are treated as characters here and
how its behavior may differ in JS.

## replacing structs with records

I replaced the logical-block struct with a record since structs are obsoleted.
Need to see if it's being mutated.

## custom deftype macro

not sure what to do with this yet.  used to create a custom constructor
`make-buffer-blob`.

## Misc

- no `(in-ns)` in cljs, so all of clojure.pprint will have to be in the same file

# References

- [original clojure.pprint source](https://github.com/clojure/clojure/tree/master/src/clj/clojure/pprint)
- [original clojure.pprint api](https://clojure.github.io/clojure/clojure.pprint-api.html)
- [differences between CLJ and CLJS](https://github.com/clojure/clojurescript/wiki/Differences-from-Clojure)

```
from #clojurescript freenode (IRC)

[13:56] <shaunlebron> anyone working on porting pprint to clojurescript?
[13:58] <dnolen_> shaunlebron: no one has stepped up to tackle that beast, a patch would be most welcome
[14:00] <shaunlebron> dnolen_: would you take a patch that just ported the simple dispatch for pretty-printing data?
[14:01] <dnolen_> shaunlebron: as long as it's done so that it doesn't cause problem for some one who wants to finish the pretty printing support
```
