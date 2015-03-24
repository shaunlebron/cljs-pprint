
(ns clojure.pprint
  (:refer-clojure :exclude [deftype])
  (:require [clojure.walk :as walk]))


;; required the following changes:
;;  replace .ppflush with -ppflush to switch from Interface to Protocol

(defmacro with-pretty-writer [base-writer & body]
  `(let [base-writer# ~base-writer
         new-writer# (not (pretty-writer? base-writer#))]
     (binding [~'*out* (if new-writer#
                         (make-pretty-writer base-writer# *print-right-margin* *print-miser-width*)
                         base-writer#)]
       ~@body
       (-ppflush ~'*out*))))


(defmacro getf
  "Get the value of the field a named by the argument (which should be a keyword)."
  [sym]
  `(~sym @@~'this))

;; change alter to swap!

(defmacro setf
  "Set the value of the field SYM to NEW-VAL"
  [sym new-val]
  `(swap! @~'this assoc ~sym ~new-val))

(defmacro deftype
  [type-name & fields]
  (let [name-str (name type-name)
        fields (map (comp symbol name) fields)]
    `(do
       (defrecord ~type-name [~'type-tag ~@fields])
       (defn- ~(symbol (str "make-" name-str))
         ~(vec fields)
         (~(symbol (str type-name ".")) ~(keyword name-str) ~@fields))
       (defn- ~(symbol (str name-str "?")) [x#] (= (:type-tag x#) ~(keyword name-str))))))

(defn- parse-lb-options [opts body]
  (loop [body body
         acc []]
    (if (opts (first body))
      (recur (drop 2 body) (concat acc (take 2 body)))
      [(apply hash-map acc) body])))

(defmacro pprint-logical-block
  "Execute the body as a pretty printing logical block with output to *out* which
  must be a pretty printing writer. When used from pprint or cl-format, this can be
  assumed.

  This function is intended for use when writing custom dispatch functions.

  Before the body, the caller can optionally specify options: :prefix, :per-line-prefix
  and :suffix."
  [& args]
  (let [[options body] (parse-lb-options #{:prefix :per-line-prefix :suffix} args)]
    `(do (if (#'clojure.pprint/level-exceeded)
           (~'-write ~'*out* "#")
           (do
             (binding [#'clojure.pprint/*current-level* (inc #'clojure.pprint/*current-level*)
                       #'clojure.pprint/*current-length* 0]
               (#'clojure.pprint/start-block #'clojure.pprint/*out*
                                             ~(:prefix options)
                                             ~(:per-line-prefix options)
                                             ~(:suffix options))
               ~@body
               (#'clojure.pprint/end-block #'clojure.pprint/*out*))))
         nil)))

(defn- pll-mod-body [var-sym body]
  (letfn [(inner [form]
                 (if (seq? form)
                   (let [form (macroexpand form)]
                     (condp = (first form)
                       'loop* form
                       'recur (concat `(recur (inc ~var-sym)) (rest form))
                       (walk/walk inner identity form)))
                   form))]
    (walk/walk inner identity body)))

(defmacro print-length-loop
  "A version of loop that iterates at most *print-length* times. This is designed
  for use in pretty-printer dispatch functions."
  [bindings & body]
  (let [count-var (gensym "length-count")
        mod-body (pll-mod-body count-var body)]
    `(loop ~(apply vector count-var 0 bindings)
       (if (or (not #'cljs.core/*print-length*) (< ~count-var #'cljs.core/*print-length*))
         (do ~@mod-body)
         (~'-write #'clojure.pprint/*out* "...")))))

