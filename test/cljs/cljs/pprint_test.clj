(ns cljs.pprint-test
  (:require
    [cemerick.cljs.test :refer [deftest is]]))

(defmacro simple-tests [name & test-pairs]
  `(deftest ~name
     ~@(for [[x y] (partition 2 test-pairs)]
         `(cond
            (= js/RegExp (type ~y)) (is (.exec ~y ~x))
            (= js/String (type ~y)) (is (= ~x ~y))
            :else (is (= ~x ~y))))))
