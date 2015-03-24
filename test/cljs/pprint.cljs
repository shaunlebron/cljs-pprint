(ns clojure.test-clojure.pprint
  (:require
    [cemerick.cljs.test :as t]
    [clojure.pprint :refer [pprint]])
  (:require-macros
    [cemerick.cljs.test :refer [deftest is]]
    [pprint-test :refer [simple-tests]]))

