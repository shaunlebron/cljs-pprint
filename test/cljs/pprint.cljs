(ns clojure.test-clojure.pprint
  (:require
    [cemerick.cljs.test :as t]
    [clojure.pprint :refer [pprint]])
  (:require-macros
    [cemerick.cljs.test :refer [deftest is]]
    [pprint-test :refer [simple-tests]]))

(simple-tests print-length-tests
  (binding [*print-length* 1] (with-out-str (pprint '(a b c d e f))))
  "(a ...)\n"
  (binding [*print-length* 2] (with-out-str (pprint '(a b c d e f))))
  "(a b ...)\n"
  (binding [*print-length* 6] (with-out-str (pprint '(a b c d e f))))
  "(a b c d e f)\n"
  (binding [*print-length* 8] (with-out-str (pprint '(a b c d e f))))
  "(a b c d e f)\n"

  (binding [*print-length* 1] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 ...]\n"
  (binding [*print-length* 2] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 ...]\n"
  (binding [*print-length* 6] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 3 4 5 6]\n"
  (binding [*print-length* 8] (with-out-str (pprint [1 2 3 4 5 6])))
  "[1 2 3 4 5 6]\n"

  (binding [*print-length* 1] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 ...}\n"
  (binding [*print-length* 2] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 ...}\n"
  (binding [*print-length* 6] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 3 4 5 6}\n"
  (binding [*print-length* 8] (with-out-str (pprint (sorted-set 1 2 3 4 5 6))))
  "#{1 2 3 4 5 6}\n"

  (binding [*print-length* 1] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, ...}\n"
  (binding [*print-length* 2] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, ...}\n"
  (binding [*print-length* 6] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, 5 6, 7 8, 9 10, 11 12}\n"
  (binding [*print-length* 8] (with-out-str (pprint (sorted-map 1 2, 3 4, 5 6, 7 8, 9 10, 11 12))))
  "{1 2, 3 4, 5 6, 7 8, 9 10, 11 12}\n"

  (binding [*print-length* 1] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
  "[1, ...]\n"
  (binding [*print-length* 2] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
  "[1, 2, ...]\n"
  (binding [*print-length* 6] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
  "[1, 2, 3, 4, 5, 6]\n"
  (binding [*print-length* 8] (with-out-str (pprint (int-array [1 2 3 4 5 6]))))
  "[1, 2, 3, 4, 5, 6]\n"
)