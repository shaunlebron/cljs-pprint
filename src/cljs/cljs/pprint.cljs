;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.pprint
  (:refer-clojure :exclude [deftype])
  (:require-macros
    [cljs.pprint :as m :refer [with-pretty-writer getf setf deftype
                               pprint-logical-block print-length-loop]])
  (:require
    [cljs.core :refer [IWriter IDeref]]
    [clojure.string :as string])
  (:import goog.string.StringBuffer))

(def ^:dynamic *out* nil)

;;======================================================================
;; cljs specific utils
;;======================================================================

(defn ^boolean float?
  "Returns true if n is an float."
  [n]
  (and (number? n)
       (not ^boolean (js/isNaN n))
       (not (identical? n js/Infinity))
       (not (== (js/parseFloat n) (js/parseInt n 10)))))

;;======================================================================
;; Utilities
;;======================================================================

(defn- map-passing-context [func initial-context lis]
  (loop [context initial-context
         lis lis
         acc []]
    (if (empty? lis)
      [acc context]
      (let [this (first lis)
            remainder (next lis)
            [result new-context] (apply func [this context])]
        (recur new-context remainder (conj acc result))))))

(defn- consume [func initial-context]
  (loop [context initial-context
         acc []]
    (let [[result new-context] (apply func [context])]
      (if (not result)
        [acc new-context])
      (recur new-context (conj acc result)))))

(defn- consume-while [func initial-context]
  (loop [context initial-context
         acc []]
    (let [[result continue new-context] (apply func [context])]
      (if (not continue)
        [acc context]
        (recur new-context (conj acc result))))))

(defn- unzip-map [m]
  "Take a  map that has pairs in the value slots and produce a pair of maps,
   the first having all the first elements of the pairs and the second all
   the second elements of the pairs"
  [(into {} (for [[k [v1 v2]] m] [k v1]))
   (into {} (for [[k [v1 v2]] m] [k v2]))])

(defn- tuple-map [m v1]
  "For all the values, v, in the map, replace them with [v v1]"
  (into {} (for [[k v] m] [k [v v1]])))

(defn- rtrim [s c]
  "Trim all instances of c from the end of sequence s"
  (let [len (count s)]
    (if (and (pos? len) (= (nth s (dec (count s))) c))
      (loop [n (dec len)]
        (cond
          (neg? n) ""
          (not (= (nth s n) c)) (subs s 0 (inc n))
          true (recur (dec n))))
      s)))

(defn- ltrim [s c]
  "Trim all instances of c from the beginning of sequence s"
  (let [len (count s)]
    (if (and (pos? len) (= (nth s 0) c))
      (loop [n 0]
        (if (or (= n len) (not (= (nth s n) c)))
          (subs s n)
          (recur (inc n))))
      s)))

(defn- prefix-count [aseq val]
  "Return the number of times that val occurs at the start of sequence aseq,
if val is a seq itself, count the number of times any element of val occurs at the
beginning of aseq"
  (let [test (if (coll? val) (set val) #{val})]
    (loop [pos 0]
      (if (or (= pos (count aseq)) (not (test (nth aseq pos))))
        pos
        (recur (inc pos))))))

;; Flush the pretty-print buffer without flushing the underlying stream
(defprotocol IPrettyFlush
  (-ppflush [pp]))

;;======================================================================
;; column_writer.clj
;;======================================================================

(def ^:dynamic ^{:private true} *default-page-width* 72)

(defn- get-field [this sym]
  (sym @@this))

(defn- set-field [this sym new-val]
  (swap! @this assoc sym new-val))

(defn- get-column [this]
  (get-field this :cur))

(defn- get-line [this]
  (get-field this :line))

(defn- get-max-column [this]
  (get-field this :max))

(defn- set-max-column [this new-max]
  (set-field this :max new-max)
  nil)

(defn- get-writer [this]
  (get-field this :base))

;; Why is the c argument an integer?
(defn- c-write-char [this c]
  (if (= c \newline)
    (do
      (set-field this :cur 0)
      (set-field this :line (inc (get-field this :line))))
    (set-field this :cur (inc (get-field this :cur))))
  (-write (get-field this :base) c))

(defn- column-writer
  ([writer] (column-writer writer *default-page-width*))
  ([writer max-columns]
   (let [fields (atom {:max max-columns, :cur 0, :line 0 :base writer})]
     (reify

       IDeref
       (-deref [_] fields)

       IWriter
       (-flush [_]
         (-flush writer))
       (-write
         ;;-write isn't multi-arity, so need different way to do this
         #_([this ^chars cbuf ^Number off ^Number len]
          (let [writer (get-field this :base)]
            (-write writer cbuf off len)))
         [this x]
         (condp = (type x)
           js/String
           (let [s x
                 nl (.lastIndexOf s \newline)]
             (if (neg? nl)
               (set-field this :cur (+ (get-field this :cur) (count s)))
               (do
                 (set-field this :cur (- (count s) nl 1))
                 (set-field this :line (+ (get-field this :line)
                                          (count (filter #(= % \newline) s))))))
             (-write (get-field this :base) s))
           js/Number
           (c-write-char this x)))))))

;;======================================================================
;; pretty_writer.clj
;;======================================================================

;;======================================================================
;; Forward declarations
;;======================================================================

(declare get-miser-width)

;;======================================================================
;; The data structures used by pretty-writer
;;======================================================================

(defrecord ^{:private true} logical-block
  [parent section start-col indent
   done-nl intra-block-nl
   prefix per-line-prefix suffix
   logical-block-callback])

(defn- ancestor? [parent child]
  (loop [child (:parent child)]
    (cond
      (nil? child) false
      (identical? parent child) true
      :else (recur (:parent child)))))

(defn- buffer-length [l]
  (let [l (seq l)]
    (if l
      (- (:end-pos (last l)) (:start-pos (first l)))
      0)))

;; A blob of characters (aka a string)
(deftype buffer-blob :data :trailing-white-space :start-pos :end-pos)

;; A newline
(deftype nl-t :type :logical-block :start-pos :end-pos)

(deftype start-block-t :logical-block :start-pos :end-pos)

(deftype end-block-t :logical-block :start-pos :end-pos)

(deftype indent-t :logical-block :relative-to :offset :start-pos :end-pos)

(def ^:private pp-newline (fn [] "\n"))

(declare emit-nl)

(defmulti ^{:private true} write-token #(:type-tag %2))

(defmethod write-token :start-block-t [this token]
  (when-let [cb (getf :logical-block-callback)] (cb :start))
  (let [lb (:logical-block token)]
    (when-let [prefix (:prefix lb)]
      (-write (getf :base) prefix))
    (let [col (get-column (getf :base))]
      (reset! (:start-col lb) col)
      (reset! (:indent lb) col))))

(defmethod write-token :end-block-t [this token]
  (when-let [cb (getf :logical-block-callback)] (cb :end))
  (when-let [suffix (:suffix (:logical-block token))]
    (-write (getf :base) suffix)))

(defmethod write-token :indent-t [this token]
  (let [lb (:logical-block token)]
    (reset! (:indent lb)
            (+ (:offset token)
               (condp = (:relative-to token)
                 :block @(:start-col lb)
                 :current (get-column (getf :base)))))))

(defmethod write-token :buffer-blob [this token]
  (-write (getf :base) (:data token)))

(defmethod write-token :nl-t [this token]
  (if (or (= (:type token) :mandatory)
          (and (not (= (:type token) :fill))
               @(:done-nl (:logical-block token))))
    (emit-nl this token)
    (if-let [tws (getf :trailing-white-space)]
      (-write (getf :base) tws)))
  (setf :trailing-white-space nil))

(defn- write-tokens [this tokens force-trailing-whitespace]
  (doseq [token tokens]
    (if-not (= (:type-tag token) :nl-t)
      (if-let [tws (getf :trailing-white-space)]
        (-write (getf :base) tws)))
    (write-token this token)
    (setf :trailing-white-space (:trailing-white-space token))
    (let [tws (getf :trailing-white-space)]
      (when (and force-trailing-whitespace tws)
        (-write (getf :base) tws)
        (setf :trailing-white-space nil)))))

;;======================================================================
;; emit-nl? method defs for each type of new line. This makes
;; the decision about whether to print this type of new line.
;;======================================================================

(defn- tokens-fit? [this tokens]
  (let [maxcol (get-max-column (getf :base))]
    (or
      (nil? maxcol)
      (< (+ (get-column (getf :base)) (buffer-length tokens)) maxcol))))

(defn- linear-nl? [this lb section]
  (or @(:done-nl lb)
      (not (tokens-fit? this section))))

(defn- miser-nl? [this lb section]
  (let [miser-width (get-miser-width this)
        maxcol (get-max-column (getf :base))]
    (and miser-width maxcol
         (>= @(:start-col lb) (- maxcol miser-width))
         (linear-nl? this lb section))))

(defmulti ^{:private true} emit-nl? (fn [t _ _ _] (:type t)))

(defmethod emit-nl? :linear [newl this section _]
  (let [lb (:logical-block newl)]
    (linear-nl? this lb section)))

(defmethod emit-nl? :miser [newl this section _]
  (let [lb (:logical-block newl)]
    (miser-nl? this lb section)))

(defmethod emit-nl? :fill [newl this section subsection]
  (let [lb (:logical-block newl)]
    (or @(:intra-block-nl lb)
        (not (tokens-fit? this subsection))
        (miser-nl? this lb section))))

(defmethod emit-nl? :mandatory [_ _ _ _]
  true)

;;======================================================================
;; Various support functions
;;======================================================================

(defn- get-section [buffer]
  (let [nl (first buffer)
        lb (:logical-block nl)
        section (seq (take-while #(not (and (nl-t? %) (ancestor? (:logical-block %) lb)))
                                 (next buffer)))]
    [section (seq (drop (inc (count section)) buffer))]))

(defn- get-sub-section [buffer]
  (let [nl (first buffer)
        lb (:logical-block nl)
        section (seq (take-while #(let [nl-lb (:logical-block %)]
                                   (not (and (nl-t? %) (or (= nl-lb lb) (ancestor? nl-lb lb)))))
                                 (next buffer)))]
    section))

(defn- update-nl-state [lb]
  (reset! (:intra-block-nl lb) true)
  (reset! (:done-nl lb) true)
  (loop [lb (:parent lb)]
    (if lb
      (do (reset! (:done-nl lb) true)
          (reset! (:intra-block-nl lb) true)
          (recur (:parent lb))))))

(defn- emit-nl [this nl]
  (-write (getf :base) (pp-newline))
  (setf :trailing-white-space nil)
  (let [lb (:logical-block nl)
        prefix (:per-line-prefix lb)]
    (if prefix
      (-write (getf :base) prefix))
    (let [istr (apply str (repeat (- @(:indent lb) (count prefix)) \space))]
      (-write (getf :base) istr))
    (update-nl-state lb)))

(defn- split-at-newline [tokens]
  (let [pre (seq (take-while #(not (nl-t? %)) tokens))]
    [pre (seq (drop (count pre) tokens))]))

;; write-token-string is called when the set of tokens in the buffer
;; is long than the available space on the line
(defn- write-token-string [this tokens]
  (let [[a b] (split-at-newline tokens)]
    (if a (write-tokens this a false))
    (if b
      (let [[section remainder] (get-section b)
            newl (first b)]
        (let [do-nl (emit-nl? newl this section (get-sub-section b))
              result (if do-nl
                       (do
                         (emit-nl this newl)
                         (next b))
                       b)
              long-section (not (tokens-fit? this result))
              result (if long-section
                       (let [rem2 (write-token-string this section)]
                         (if (= rem2 section)
                           (do ; If that didn't produce any output, it has no nls
                             ; so we'll force it
                             (write-tokens this section false)
                             remainder)
                           (into [] (concat rem2 remainder))))
                       result)]
          result)))))

(defn- write-line [this]
  (loop [buffer (getf :buffer)]
    (setf :buffer (into [] buffer))
    (if (not (tokens-fit? this buffer))
      (let [new-buffer (write-token-string this buffer)]
        (if-not (identical? buffer new-buffer)
          (recur new-buffer))))))

;; Add a buffer token to the buffer and see if it's time to start
;; writing
(defn- add-to-buffer [this token]
  (setf :buffer (conj (getf :buffer) token))
  (if (not (tokens-fit? this (getf :buffer)))
    (write-line this)))

;; Write all the tokens that have been buffered
(defn- write-buffered-output [this]
  (write-line this)
  (if-let [buf (getf :buffer)]
    (do
      (write-tokens this buf true)
      (setf :buffer []))))

(defn- write-white-space [this]
  (when-let [tws (getf :trailing-white-space)]
    (-write (getf :base) tws)
    (setf :trailing-white-space nil)))

;;; If there are newlines in the string, print the lines up until the last newline,
;;; making the appropriate adjustments. Return the remainder of the string
(defn- write-initial-lines
  [^Writer this ^String s]
  (let [lines (string/split s "\n" -1)]
    (if (= (count lines) 1)
      s
      (let [^String prefix (:per-line-prefix (first (getf :logical-blocks)))
            ^String l (first lines)]
        (if (= :buffering (getf :mode))
          (let [oldpos (getf :pos)
                newpos (+ oldpos (count l))]
            (setf :pos newpos)
            (add-to-buffer this (make-buffer-blob l nil oldpos newpos))
            (write-buffered-output this))
          (do
            (write-white-space this)
            (-write (getf :base) l)))
        (-write (getf :base) \newline)
        (doseq [^String l (next (butlast lines))]
          (-write (getf :base) l)
          (-write (getf :base) (pp-newline))
          (if prefix
            (-write (getf :base) prefix)))
        (setf :buffering :writing)
        (last lines)))))

(defn- p-write-char [this c]
  (if (= (getf :mode) :writing)
    (do
      (write-white-space this)
      (-write (getf :base) c))
    (if (= c \newline)
      (write-initial-lines this \newline)
      (let [oldpos (getf :pos)
            newpos (inc oldpos)]
        (setf :pos newpos)
        (add-to-buffer this (make-buffer-blob (str (char c)) nil oldpos newpos))))))

;;======================================================================
;; Initialize the pretty-writer instance
;;======================================================================

(defn- pretty-writer [writer max-columns miser-width]
  (let [lb (logical-block. nil nil (atom 0) (atom 0) (atom false) (atom false)
                           nil nil nil nil)
        ; NOTE: may want to just `specify!` #js { ... fields ... } with the protocols
        fields (atom {:pretty-writer true
                      :base (column-writer writer max-columns)
                      :logical-blocks lb
                      :sections nil
                      :mode :writing
                      :buffer []
                      :buffer-block lb
                      :buffer-level 1
                      :miser-width miser-width
                      :trailing-white-space nil
                      :pos 0})]
    (reify

      IDeref
      (-deref [_] fields)

      IWriter
      (-write [this x]
        (condp = (type x)
          js/String
          (let [s0 (write-initial-lines this x)
                s (string/replace-first s0 #"\s+$" "")
                white-space (subs s0 (count s))
                mode (getf :mode)]
            (if (= mode :writing)
              (do
                (write-white-space this)
                (-write (getf :base) s)
                (setf :trailing-white-space white-space))
              (let [oldpos (getf :pos)
                    newpos (+ oldpos (count s0))]
                (setf :pos newpos)
                (add-to-buffer this (make-buffer-blob s white-space oldpos newpos)))))
          js/Number
          (p-write-char this x)))
      (-flush [this]
        (-ppflush this)
        (-flush (getf :base)))

      IPrettyFlush
      (-ppflush [this]
        (if (= (getf :mode) :buffering)
          (do
            (write-tokens this (getf :buffer) true)
            (setf :buffer []))
          (write-white-space this)))

      )))

;;======================================================================
;; Methods for pretty-writer
;;======================================================================

(defn- start-block
  [this prefix per-line-prefix suffix]
  (let [lb (logical-block. (getf :logical-blocks) nil (atom 0) (atom 0)
                           (atom false) (atom false)
                           prefix per-line-prefix suffix nil)]
    (setf :logical-blocks lb)
    (if (= (getf :mode) :writing)
      (do
        (write-white-space this)
        (when-let [cb (getf :logical-block-callback)] (cb :start))
        (if prefix
          (-write (getf :base) prefix))
        (let [col (get-column (getf :base))]
          (reset! (:start-col lb) col)
          (reset! (:indent lb) col)))
      (let [oldpos (getf :pos)
            newpos (+ oldpos (if prefix (count prefix) 0))]
        (setf :pos newpos)
        (add-to-buffer this (make-start-block-t lb oldpos newpos))))))

(defn- end-block [this]
  (let [lb (getf :logical-blocks)
        suffix (:suffix lb)]
    (if (= (getf :mode) :writing)
      (do
        (write-white-space this)
        (if suffix
          (-write (getf :base) suffix))
        (when-let [cb (getf :logical-block-callback)] (cb :end)))
      (let [oldpos (getf :pos)
            newpos (+ oldpos (if suffix (count suffix) 0))]
        (setf :pos newpos)
        (add-to-buffer this (make-end-block-t lb oldpos newpos))))
    (setf :logical-blocks (:parent lb))))

(defn- nl [this type]
  (setf :mode :buffering)
  (let [pos (getf :pos)]
    (add-to-buffer this (make-nl-t type (getf :logical-blocks) pos pos))))

(defn- indent [this relative-to offset]
  (let [lb (getf :logical-blocks)]
    (if (= (getf :mode) :writing)
      (do
        (write-white-space this)
        (reset! (:indent lb)
                (+ offset (condp = relative-to
                            :block @(:start-col lb)
                            :current (get-column (getf :base))))))
      (let [pos (getf :pos)]
        (add-to-buffer this (make-indent-t lb relative-to offset pos pos))))))

(defn- get-miser-width [this]
  (getf :miser-width))

;;======================================================================
;; pprint_base.clj
;;======================================================================

;;======================================================================
;; Variables that control the pretty printer
;;======================================================================

;; *print-length*, *print-level* and *print-dup* are defined in cljs.core
(def ^:dynamic
 ^{:doc "Bind to true if you want write to use pretty printing"}
 *print-pretty* true)

(defonce ^:dynamic
 ^{:doc "The pretty print dispatch function. Use with-pprint-dispatch or
set-pprint-dispatch to modify."
   :added "1.2"}
 *print-pprint-dispatch* nil)

(def ^:dynamic
 ^{:doc "Pretty printing will try to avoid anything going beyond this column.
Set it to nil to have pprint let the line be arbitrarily long. This will ignore all
non-mandatory newlines.",
   :added "1.2"}
 *print-right-margin* 72)

(def ^:dynamic
 ^{:doc "The column at which to enter miser style. Depending on the dispatch table,
miser style add newlines in more places to try to keep lines short allowing for further
levels of nesting.",
   :added "1.2"}
 *print-miser-width* 40)

;;; TODO implement output limiting
(def ^:dynamic
^{:private true,
  :doc "Maximum number of lines to print in a pretty print instance (N.B. This is not yet used)"}
*print-lines* nil)

;;; TODO: implement circle and shared
(def ^:dynamic
^{:private true,
  :doc "Mark circular structures (N.B. This is not yet used)"}
*print-circle* nil)

;;; TODO: should we just use *print-dup* here?
(def ^:dynamic
^{:private true,
  :doc "Mark repeated structures rather than repeat them (N.B. This is not yet used)"}
*print-shared* nil)

(def ^:dynamic
^{:doc "Don't print namespaces with symbols. This is particularly useful when
pretty printing the results of macro expansions"
  :added "1.2"}
*print-suppress-namespaces* nil)

;;; TODO: support print-base and print-radix in cl-format
;;; TODO: support print-base and print-radix in rationals
(def ^:dynamic
^{:doc "Print a radix specifier in front of integers and rationals. If *print-base* is 2, 8,
or 16, then the radix specifier used is #b, #o, or #x, respectively. Otherwise the
radix specifier is in the form #XXr where XX is the decimal value of *print-base* "
  :added "1.2"}
*print-radix* nil)

(def ^:dynamic
^{:doc "The base to use for printing integers and rationals."
  :added "1.2"}
*print-base* 10)

;;======================================================================
;; Internal variables that keep track of where we are in the
;; structure
;;======================================================================

(def ^:dynamic ^{:private true} *current-level* 0)

(def ^:dynamic ^{:private true} *current-length* nil)

;;======================================================================
;; Support for the write function
;;======================================================================

(defn- pretty-writer?
  "Return true iff x is a PrettyWriter"
  [x] (and (satisfies? IDeref x) (:pretty-writer @@x)))

(defn- make-pretty-writer
  "Wrap base-writer in a PrettyWriter with the specified right-margin and miser-width"
  [base-writer right-margin miser-width]
  (pretty-writer base-writer right-margin miser-width))

(defn write-out
  "Write an object to *out* subject to the current bindings of the printer control
variables. Use the kw-args argument to override individual variables for this call (and
any recursive calls).

*out* must be a PrettyWriter if pretty printing is enabled. This is the responsibility
of the caller.

This method is primarily intended for use by pretty print dispatch functions that
already know that the pretty printer will have set up their environment appropriately.
Normal library clients should use the standard \"write\" interface. "
  [object]
  (let [length-reached (and *current-length*
                            *print-length*
                            (>= *current-length* *print-length*))]
    (if-not *print-pretty*
      (pr object) ;;TODO this needs to go to *out* I think
      (if length-reached
        (-write *out* "...") ;;TODO could this (incorrectly) print ... on the next line?
        (do
          (if *current-length* (set! *current-length* (inc *current-length*)))
          (*print-pprint-dispatch* object))))
    length-reached))

(defn pprint
  ([object]
   (let [sb (StringBuffer.)]
     (binding [*out* (StringBufferWriter. sb)]
       (pprint object *out*)
       (*print-fn* (str sb)))))
  ([object writer]
   (with-pretty-writer writer
                       (binding [*print-pretty* true]
                         (write-out object))
                       (if (not (= 0 (get-column *out*)))
                         (-write *out* \newline)))))

(defn set-pprint-dispatch
  [function]
  (set! *print-pprint-dispatch* function)
  nil)

;;======================================================================
;; Support for the functional interface to the pretty printer
;;======================================================================

(defn- check-enumerated-arg [arg choices]
  (if-not (choices arg)
    ;; TODO clean up choices string
    (throw (js/Error. (str "Bad argument: " arg ". It must be one of " choices)))))

(defn- level-exceeded []
  (and *print-level* (>= *current-level* *print-level*)))

(defn pprint-newline
  "Print a conditional newline to a pretty printing stream. kind specifies if the
  newline is :linear, :miser, :fill, or :mandatory.

  This function is intended for use when writing custom dispatch functions.

  Output is sent to *out* which must be a pretty printing writer."
  [kind]
  (check-enumerated-arg kind #{:linear :miser :fill :mandatory})
  (nl *out* kind))

;;======================================================================
;; cl_format.clj
;;======================================================================

;; Forward references
(declare compile-format)
(declare execute-format)
(declare init-navigator)
;; End forward references

(defn cl-format
  "An implementation of a Common Lisp compatible format function. cl-format formats its
arguments to an output stream or string based on the format control string given. It
supports sophisticated formatting of structured data.

Writer satisfies IWriter, true to output via *print-fn* or nil to output
to a string, format-in is the format control string and the remaining arguments
are the data to be formatted.

The format control string is a string to be output with embedded 'format directives'
describing how to format the various arguments passed in.

If writer is nil, cl-format returns the formatted result string. Otherwise, cl-format
returns nil.

For example:
 (let [results [46 38 22]]
        (cl-format true \"There ~[are~;is~:;are~]~:* ~d result~:p: ~{~d~^, ~}~%\"
                   (count results) results))

Prints via *print-fn*:
 There are 3 results: 46, 38, 22

Detailed documentation on format control strings is available in the \"Common Lisp the
Language, 2nd edition\", Chapter 22 (available online at:
http://www.cs.cmu.edu/afs/cs.cmu.edu/project/ai-repository/ai/html/cltl/clm/node200.html#SECTION002633000000000000000)
and in the Common Lisp HyperSpec at
http://www.lispworks.com/documentation/HyperSpec/Body/22_c.htm"
  {:see-also [["http://www.cs.cmu.edu/afs/cs.cmu.edu/project/ai-repository/ai/html/cltl/clm/node200.html#SECTION002633000000000000000"
               "Common Lisp the Language"]
              ["http://www.lispworks.com/documentation/HyperSpec/Body/22_c.htm"
               "Common Lisp HyperSpec"]]}
  [writer format-in & args]
  (let [compiled-format (if (string? format-in) (compile-format format-in) format-in)
        navigator (init-navigator args)]
    (execute-format writer compiled-format navigator)))

(def ^:dynamic ^{:private true} *format-str* nil)

(defn- format-error [message offset]
  (let [full-message (str message \newline *format-str* \newline
                          (apply str (repeat offset \space)) "^" \newline)]
    (throw (js/Error full-message))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Argument navigators manage the argument list
;; as the format statement moves through the list
;; (possibly going forwards and backwards as it does so)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ^{:private true}
  arg-navigator [seq rest pos])

(defn- init-navigator
  "Create a new arg-navigator from the sequence with the position set to 0"
  {:skip-wiki true}
  [s]
  (let [s (seq s)]
    (arg-navigator. s s 0)))

;; TODO call format-error with offset
(defn- next-arg [navigator]
  (let [rst (:rest navigator)]
    (if rst
      [(first rst) (arg-navigator. (:seq navigator) (next rst) (inc (:pos navigator)))]
      (throw (js/Error "Not enough arguments for format definition")))))

(defn- next-arg-or-nil [navigator]
  (let [rst (:rest navigator)]
    (if rst
      [(first rst) (arg-navigator. (:seq navigator) (next rst) (inc (:pos navigator)))]
      [nil navigator])))

;; Get an argument off the arg list and compile it if it's not already compiled
(defn- get-format-arg [navigator]
  (let [[raw-format navigator] (next-arg navigator)
        compiled-format (if (string? raw-format)
                          (compile-format raw-format)
                          raw-format)]
    [compiled-format navigator]))

(declare relative-reposition)

(defn- absolute-reposition [navigator position]
  (if (>= position (:pos navigator))
    (relative-reposition navigator (- (:pos navigator) position))
    (arg-navigator. (:seq navigator) (drop position (:seq navigator)) position)))

(defn- relative-reposition [navigator position]
  (let [newpos (+ (:pos navigator) position)]
    (if (neg? position)
      (absolute-reposition navigator newpos)
      (arg-navigator. (:seq navigator) (drop position (:rest navigator)) newpos))))

(defrecord ^{:private true}
  compiled-directive [func def params offset])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; When looking at the parameter list, we may need to manipulate
;; the argument list as well (for 'V' and '#' parameter types).
;; We hide all of this behind a function, but clients need to
;; manage changing arg navigator
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: validate parameters when they come from arg list
(defn- realize-parameter [[param [raw-val offset]] navigator]
  (let [[real-param new-navigator]
        (cond
          (contains? #{:at :colon} param) ;pass flags through unchanged - this really isn't necessary
          [raw-val navigator]

          (= raw-val :parameter-from-args)
          (next-arg navigator)

          (= raw-val :remaining-arg-count)
          [(count (:rest navigator)) navigator]

          true
          [raw-val navigator])]
    [[param [real-param offset]] new-navigator]))

(defn- realize-parameter-list [parameter-map navigator]
  (let [[pairs new-navigator]
        (map-passing-context realize-parameter navigator parameter-map)]
    [(into {} pairs) new-navigator]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions that support individual directives
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Common handling code for ~A and ~S
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare opt-base-str)

(def ^{:private true}
  special-radix-markers {2 "#b" 8 "#o" 16 "#x"})

(defn- format-simple-number [n]
  (cond
    (integer? n) (if (= *print-base* 10)
                   (str n (if *print-radix* "."))
                   (str
                     (if *print-radix* (or (get special-radix-markers *print-base*) (str "#" *print-base* "r")))
                     (opt-base-str *print-base* n)))
    ;;(ratio? n) ;;no ratio support
    :else nil))

(defn- format-ascii [print-func params arg-navigator offsets]
  (let [[arg arg-navigator] (next-arg arg-navigator)
        base-output (or (format-simple-number arg) (print-func arg))
        base-width (.-length base-output)
        min-width (+ base-width (:minpad params))
        width (if (>= min-width (:mincol params))
                min-width
                (+ min-width
                   (* (+ (quot (- (:mincol params) min-width 1)
                               (:colinc params))
                         1)
                      (:colinc params))))
        chars (apply str (repeat (- width base-width) (:padchar params)))]
    (if (:at params)
      (print (str chars base-output))  ;;TODO need to print to *out*
      (print (str base-output chars))) ;;TODO need to print to *out*
    arg-navigator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for the integer directives ~D, ~X, ~O, ~B and some
;; of ~R
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- integral?
  "returns true if a number is actually an integer (that is, has no fractional part)"
  [x]
  (cond
    (integer? x) true
    ;;(decimal? x) ;;no decimal support
    (float? x) (= x (Math/floor x))
    ;;(ratio? x) ;;no ratio support
    :else false))

(defn- remainders
  "Return the list of remainders (essentially the 'digits') of val in the given base"
  [base val]
  (reverse
    (first
      (consume #(if (pos? %)
                 [(rem % base) (quote %) base]
                 [nil nil])
               val))))

;; TODO: xlated-val does not seem to be used here.
;; NB
(defn- base-str
  "Return val as a string in the given base"
  [base val]
  (if (zero? val)
    "0"
    (let [xlated-val (cond
                       ;(float? val) (bigdec val) ;;No bigdec
                       ;(ratio? val) nil ;;No ratio
                       :else val)]
      (apply str
             (map
               #(if (< % 10) (char (+ (int \0) %)) (char (+ (int \a) (- % 10))))
               (remainders base val))))))

;;Not sure if this is accurate or necessary
(def ^{:private true}
  javascript-base-formats {8 "%o", 10 "%d", 16 "%x"})

(defn- opt-base-str
  "Return val as a string in the given base. No cljs format, so no improved performance."
  [base val]
  (base-str base val))

(defn- group-by* [unit lis]
  (reverse
    (first
      (consume (fn [x] [(seq (reverse (take unit x))) (seq (drop unit x))]) (reverse lis)))))

(defn- format-integer [base params arg-navigator offsets]
  (let [[arg arg-navigator] (next-arg arg-navigator)]
    (if (integral? arg)
      (let [neg (neg? arg)
            pos-arg (if neg (- arg) arg)
            raw-str (opt-base-str base pos-arg)
            group-str (if (:colon params)
                        (let [groups (map #(apply str %) (group-by* (:commainterval params) raw-str))
                              commas (repeat (count groups) (:commachar params))]
                          (apply str (next (interleave commas groups))))
                        raw-str)
            signed-str (cond
                         neg (str "-" group-str)
                         (:at params) (str "+" group-str)
                         true group-str)
            padded-str (if (< (.-length signed-str) (:mincol params))
                         (str (apply str (repeat (- (:mincol params) (.-length signed-str))
                                                 (:padchar params)))
                              signed-str)
                         signed-str)]
        (print padded-str))  ;;TODO need to print to *out*
      (format-ascii print-str {:mincol (:mincol params) :colinc 1 :minpad 0
                               :padchar (:padchar params) :at true}
                    (init-navigator [arg]) nil))
    arg-navigator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for english formats (~R and ~:R)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:private true}
     english-cardinal-units
     ["zero" "one" "two" "three" "four" "five" "six" "seven" "eight" "nine"
      "ten" "eleven" "twelve" "thirteen" "fourteen"
      "fifteen" "sixteen" "seventeen" "eighteen" "nineteen"])

(def ^{:private true}
     english-ordinal-units
     ["zeroth" "first" "second" "third" "fourth" "fifth" "sixth" "seventh" "eighth" "ninth"
      "tenth" "eleventh" "twelfth" "thirteenth" "fourteenth"
      "fifteenth" "sixteenth" "seventeenth" "eighteenth" "nineteenth"])

(def ^{:private true}
     english-cardinal-tens
     ["" "" "twenty" "thirty" "forty" "fifty" "sixty" "seventy" "eighty" "ninety"])

(def ^{:private true}
     english-ordinal-tens
     ["" "" "twentieth" "thirtieth" "fortieth" "fiftieth"
      "sixtieth" "seventieth" "eightieth" "ninetieth"])

;; We use "short scale" for our units (see http://en.wikipedia.org/wiki/Long_and_short_scales)
;; Number names from http://www.jimloy.com/math/billion.htm
;; We follow the rules for writing numbers from the Blue Book
;; (http://www.grammarbook.com/numbers/numbers.asp)
(def ^{:private true}
     english-scale-numbers
     ["" "thousand" "million" "billion" "trillion" "quadrillion" "quintillion"
      "sextillion" "septillion" "octillion" "nonillion" "decillion"
      "undecillion" "duodecillion" "tredecillion" "quattuordecillion"
      "quindecillion" "sexdecillion" "septendecillion"
      "octodecillion" "novemdecillion" "vigintillion"])

(defn- format-simple-cardinal
  "Convert a number less than 1000 to a cardinal english string"
  [num]
  (let [hundreds (quot num 100)
        tens (rem num 100)]
    (str
      (if (pos? hundreds) (str (nth english-cardinal-units hundreds) " hundred"))
      (if (and (pos? hundreds) (pos? tens)) " ")
      (if (pos? tens)
        (if (< tens 20)
          (nth english-cardinal-units tens)
          (let [ten-digit (quot tens 10)
                unit-digit (rem tens 10)]
            (str
              (if (pos? ten-digit) (nth english-cardinal-tens ten-digit))
              (if (and (pos? ten-digit) (pos? unit-digit)) "-")
              (if (pos? unit-digit) (nth english-cardinal-units unit-digit)))))))))

(defn- add-english-scales
  "Take a sequence of parts, add scale numbers (e.g., million) and combine into a string
  offset is a factor of 10^3 to multiply by"
  [parts offset]
  (let [cnt (count parts)]
    (loop [acc []
           pos (dec cnt)
           this (first parts)
           remainder (next parts)]
      (if (nil? remainder)
        (str (apply str (interpose ", " acc))
             (if (and (not (empty? this)) (not (empty? acc))) ", ")
             this
             (if (and (not (empty? this)) (pos? (+ pos offset)))
               (str " " (nth english-scale-numbers (+ pos offset)))))
        (recur
          (if (empty? this)
            acc
            (conj acc (str this " " (nth english-scale-numbers (+ pos offset)))))
          (dec pos)
          (first remainder)
          (next remainder))))))

(defn- format-cardinal-english [params navigator offsets]
  (let [[arg navigator] (next-arg navigator)]
    (if (= 0 arg)
      (print "zero")  ;;TODO print to *out*
      (let [abs-arg (if (neg? arg) (- arg) arg) ; some numbers are too big for Math/abs (is this true?)
            parts (remainders 1000 abs-arg)]
        (if (<= (count parts) (count english-scale-numbers))
          (let [parts-strs (map format-simple-cardinal parts)
                full-str (add-english-scales parts-strs 0)]
            (print (str (if (neg? arg) "minus ") full-str)))  ;;TODO print to *out*
          (format-integer ;; for numbers > 10^63, we fall back on ~D
            10
            {:mincol 0, :padchar \space, :commachar \, :commainterval 3, :colon true}
            (init-navigator [arg])
            {:mincol 0, :padchar 0, :commachar 0 :commainterval 0}))))
    navigator))

(defn- format-simple-ordinal
  "Convert a number less than 1000 to a ordinal english string
  Note this should only be used for the last one in the sequence"
  [num]
  (let [hundreds (quot num 100)
        tens (rem num 100)]
    (str
      (if (pos? hundreds) (str (nth english-cardinal-units hundreds) " hundred"))
      (if (and (pos? hundreds) (pos? tens)) " ")
      (if (pos? tens)
        (if (< tens 20)
          (nth english-ordinal-units tens)
          (let [ten-digit (quot tens 10)
                unit-digit (rem tens 10)]
            (if (and (pos? ten-digit) (not (pos? unit-digit)))
              (nth english-ordinal-units ten-digit)
              (str
                (if (pos? ten-digit) (nth english-cardinal-tens ten-digit))
                (if (and (pos? ten-digit) (pos? unit-digit)) "-")
                (if (pos? unit-digit) (nth english-ordinal-units unit-digit))))))
        (if (pos? hundreds) "th")))))

(defn- format-ordinal-english [params navigator offsets]
  (let [[arg navigator] (next-arg navigator)]
    (if (= 0 arg)
      (print "zeroth")  ;;TODO print to *out*
      (let [abs-arg (if (neg? arg) (- arg) arg) ; some numbers are too big for Math/abs (is this true?)
            parts (remainders 1000 abs-arg)]
        (if (<= (count parts) (count english-scale-numbers))
          (let [parts-strs (map format-simple-cardinal (drop-last parts))
                head-str (add-english-scales parts-strs 1)
                tail-str (format-simple-ordinal (last parts))]
            (print (str (if (neg? arg) "minus ")  ;;TODO print to *out*
                        (cond
                          (and (not (empty? head-str)) (not (empty? tail-str)))
                          (str head-str ", " tail-str)

                          (not (empty? head-str)) (str head-str "th")
                          :else tail-str))))
          (do (format-integer ;for numbers > 10^63, we fall back on ~D
                10
                {:mincol 0, :padchar \space, :commachar \, :commainterval 3, :colon true}
                (init-navigator [arg])
                {:mincol 0, :padchar 0, :commachar 0 :commainterval 0})
              (let [low-two-digits (rem arg 100)
                    not-teens (or (< 11 low-two-digits) (> 19 low-two-digits))
                    low-digit (rem low-two-digits 10)]
                (print (cond   ;;TODO print to *out*
                         (and (== low-digit 1) not-teens) "st"
                         (and (== low-digit 2) not-teens) "nd"
                         (and (== low-digit 3) not-teens) "rd"
                         :else "th")))))))
    navigator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for roman numeral formats (~@R and ~@:R)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:private true}
     old-roman-table
     [[ "I" "II" "III" "IIII" "V" "VI" "VII" "VIII" "VIIII"]
      [ "X" "XX" "XXX" "XXXX" "L" "LX" "LXX" "LXXX" "LXXXX"]
      [ "C" "CC" "CCC" "CCCC" "D" "DC" "DCC" "DCCC" "DCCCC"]
      [ "M" "MM" "MMM"]])

(def ^{:private true}
     new-roman-table
     [[ "I" "II" "III" "IV" "V" "VI" "VII" "VIII" "IX"]
      [ "X" "XX" "XXX" "XL" "L" "LX" "LXX" "LXXX" "XC"]
      [ "C" "CC" "CCC" "CD" "D" "DC" "DCC" "DCCC" "CM"]
      [ "M" "MM" "MMM"]])

(defn- format-roman
  "Format a roman numeral using the specified look-up table"
  [table params navigator offsets]
  (let [[arg navigator] (next-arg navigator)]
    (if (and (number? arg) (> arg 0) (< arg 4000))
      (let [digits (remainders 10 arg)]
        (loop [acc []
               pos (dec (count digits))
               digits digits]
          (if (empty? digits)
            (print (apply str acc))  ;;TODO print to *out*
            (let [digit (first digits)]
              (recur (if (= 0 digit)
                       acc
                       (conj acc (nth (nth table pos) (dec digit))))
                     (dec pos)
                     (next digits))))))
      (format-integer ; for anything <= 0 or > 3999, we fall back on ~D
        10
        {:mincol 0, :padchar \space, :commachar \, :commainterval 3, :colon true}
        (init-navigator [arg])
        {:mincol 0, :padchar 0, :commachar 0 :commainterval 0}))
    navigator))

(defn- format-old-roman [params navigator offsets]
  (format-roman old-roman-table params navigator offsets))

(defn- format-new-roman [params navigator offsets]
  (format-roman new-roman-table params navigator offsets))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for character formats (~C)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^{:private true}
     special-chars {8 "Backspace", 9 "Tab", 10 "Newline", 13 "Return", 32 "Space"})

(defn- pretty-character [params navigator offsets]
  (let [[c navigator] (next-arg navigator)
        as-int (int c)
        base-char (bit-and as-int 127)
        meta (bit-and as-int 128)
        special (get special-chars base-char)]
    (if (> meta 0) (print "Meta-")) ;;TODO print to *out*
    (print (cond  ;;TODO print to *out*
             special special
             (< base-char 32) (str "Control-" (char (+ base-char 64)))
             (= base-char 127) "Control-?"
             :else (char base-char)))
    navigator))

(defn- readable-character [params navigator offsets]
  (let [[c navigator] (next-arg navigator)]
    (condp = (:char-format params)
      \o (cl-format true "\\o~3, '0o" (int c))
      \u (cl-format true "\\u~4, '0x" (int c))
      nil (pr c))  ;;TODO print to *out*
    navigator))

(defn- plain-character [params navigator offsets]
  (let [[char navigator] (next-arg navigator)]
    (print char)   ;;TODO print to *out*
    navigator))

;; Check to see if a result is an abort (~^) construct
;; TODO: move these funcs somewhere more appropriate
(defn- abort? [context]
  (let [token (first context)]
    (or (= :up-arrow token) (= :colon-up-arrow token))))

;; Handle the execution of "sub-clauses" in bracket constructions
(defn- execute-sub-format [format args base-args]
  (second
    (map-passing-context
      (fn [element context]
        (if (abort? context)
          [nil context]    ; just keep passing it along
          (let [[params args] (realize-parameter-list (:params element) context)
                [params offsets] (unzip-map params)
                params (assoc params :base-args base-args)]
            [nil (apply (:func element) [params args offsets])])))
      args
      format)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for real number formats
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO - return exponent as int to eliminate double conversion
(defn- float-parts-base
  "Produce string parts for the mantissa (normalize 1-9) and exponent"
  [f]
  (let [s (string/lower-case (str f))
        exploc (.indexOf s \e)
        dotloc (.indexOf s \.)]
    (if (neg? exploc)
      (if (neg? dotloc)
        [s (str (dec (count s)))]
        [(str (subs s 0 dotloc) (subs s (inc dotloc))) (str (dec dotloc))])
      (if (neg? dotloc)
        [(subs s 0 exploc) (subs s (inc exploc))]
        [(str (subs s 0 1) (subs s 2 exploc)) (subs s (inc exploc))]))))

(defn- float-parts
  "Take care of leading and trailing zeros in decomposed floats"
  [f]
  (let [[m e] (float-parts-base f)
        m1 (rtrim m \0)
        m2 (ltrim m1 \0)
        delta (- (count m1) (count m2))
        e (if (and (pos? (count e)) (= (nth e 0) \+)) (subs e 1) e)]
    (if (empty? m2)
      ["0" 0]
      [m2 (- (js/parseInt e) delta)])))

(defn- inc-s
  "Assumption: The input string consists of one or more decimal digits,
  and no other characters. Return a string containing one or more
  decimal digits containing a decimal number one larger than the input
  string. The output string will always be the same length as the input
  string, or one character longer."
  [s]
  (let [len-1 (dec (count s))]
    (loop [i (int len-1)]
      (cond
        (neg? i) (apply str "1" (repeat (inc len-1) "0"))
        (= \9 (.charAt s i)) (recur (dec i))
        :else (apply str (subs s 0 i)
                     (char (inc (int (.charAt s i))))
                     (repeat (- len-1 i) "0"))))))

(defn- round-str [m e d w]
  (if (or d w)
    (let [len (count m)
          ;; Every formatted floating point number should include at
          ;; least one decimal digit and a decimal point.
          ;; TODO: NB: This is a *bug* in the original code. w is used in
          ;; calculations below but could potentially be nil. cljs gives
          ;; compilation warnings.
          w (if w (max 2 w))
          round-pos (cond
                      ;; If d was given, that forces the rounding
                      ;; position, regardless of any width that may
                      ;; have been specified.
                      d (+ e d 1)
                      ;; Otherwise w was specified, so pick round-pos
                      ;; based upon that.
                      ;; If e>=0, then abs value of number is >= 1.0,
                      ;; and e+1 is number of decimal digits before the
                      ;; decimal point when the number is written
                      ;; without scientific notation. Never round the
                      ;; number before the decimal point.
                      (>= e 0) (max (inc e) (dec w))
                      ;; e < 0, so number abs value < 1.0
                      :else (+ w e))
          [m1 e1 round-pos len] (if (= round-pos 0)
                                  [(str "0" m) (inc e) 1 (inc len)]
                                  [m e round-pos len])]
      (if round-pos
        (if (neg? round-pos)
          ["0" 0 false]
          (if (> len round-pos)
            (let [round-char (nth m1 round-pos)
                  result (subs m1 0 round-pos)]
              (if (>= (int round-char) (int \5))
                (let [round-up-result (inc-s result)
                      expanded (> (count round-up-result) (count result))]
                  [(if expanded
                     (subs round-up-result 0 (dec (count round-up-result)))
                     round-up-result)
                   e1 expanded])
                [result e1 false]))
            [m e false]))
        [m e false]))
    [m e false]))

(defn- expand-fixed [m e d]
  (let [[m1 e1] (if (neg? e)
                  [(str (apply str (repeat (dec (- e)) \0)) m) -1]
                  [m e])
        len (count m1)
        target-len (if d (+ e1 d 1) (inc e1))]
    (if (< len target-len)
      (str m1 (apply str (repeat (- target-len len) \0)))
      m1)))

(defn- insert-decimal
  "Insert the decimal point at the right spot in the number to match an exponent"
  [m e]
  (if (neg? e)
    (str "." m)
    (let [loc (inc e)]
      (str (subs m 0 loc) "." (subs m loc)))))

(defn- get-fixed [m e d]
  (insert-decimal (expand-fixed m e d) e))

(defn- insert-scaled-decimal
  "Insert the decimal point at the right spot in the number to match an exponent"
  [m k]
  (if (neg? k)
    (str "." m)
    (str (subs m 0 k) "." (subs m k))))

;;TODO: No ratio, so not sure what to do here
(defn- convert-ratio [x]
  x)

;; the function to render ~F directives
;; TODO: support rationals. Back off to ~D/~A in the appropriate cases
(defn- fixed-float [params navigator offsets]
  (let [w (:w params)
        d (:d params)
        [arg navigator] (next-arg navigator)
        [sign abs] (if (neg? arg) ["-" (- arg)] ["+" arg])
        abs (convert-ratio abs)
        [mantissa exp] (float-parts abs)
        scaled-exp (+ exp (:k params))
        add-sign (or (:at params) (neg? arg))
        append-zero (and (not d) (<= (dec (count mantissa)) scaled-exp))
        [rounded-mantissa scaled-exp expanded] (round-str mantissa scaled-exp
                                                          d (if w (- w (if add-sign 1 0))))
        fixed-repr (get-fixed rounded-mantissa (if expanded (inc scaled-exp) scaled-exp) d)
        fixed-repr (if (and w d
                            (>= d 1)
                            (= (.charAt fixed-repr 0) \0)
                            (= (.charAt fixed-repr 1) \.)
                            (> (count fixed-repr) (- w (if add-sign 1 0))))
                     (subs fixed-repr 1)    ;chop off leading 0
                     fixed-repr)
        prepend-zero (= (first fixed-repr) \.)]
    (if w
      (let [len (count fixed-repr)
            signed-len (if add-sign (inc len) len)
            prepend-zero (and prepend-zero (not (>= signed-len w)))
            append-zero (and append-zero (not (>= signed-len w)))
            full-len (if (or prepend-zero append-zero)
                       (inc signed-len)
                       signed-len)]
        (if (and (> full-len w) (:overflowchar params))
          (print (apply str (repeat w (:overflowchar params)))) ;;TODO print to *out*
          (print (str   ;;TODO print to *out*
                   (apply str (repeat (- w full-len) (:padchar params)))
                   (if add-sign sign)
                   (if prepend-zero "0")
                   fixed-repr
                   (if append-zero "0")))))
      (print (str   ;;TODO print to *out*
               (if add-sign sign)
               (if prepend-zero "0")
               fixed-repr
               (if append-zero "0"))))
    navigator))

;; the function to render ~E directives
;; TODO: support rationals. Back off to ~D/~A in the appropriate cases
;; TODO: define ~E representation for Infinity
(defn- exponential-float [params navigator offset]
  (let [[arg navigator] (next-arg navigator)
        arg (convert-ratio arg)]
    (loop [[mantissa exp] (float-parts (if (neg? arg) (- arg) arg))]
      (let [w (:w params)
            d (:d params)
            e (:e params)
            k (:k params)
            expchar (or (:exponentchar params) \E)
            add-sign (or (:at params) (neg? arg))
            prepend-zero (<= k 0)
            scaled-exp (- exp (dec k))
            scaled-exp-str (str (Math/abs scaled-exp))
            scaled-exp-str (str expchar (if (neg? scaled-exp) \- \+)
                                (if e (apply str
                                             (repeat
                                               (- e
                                                  (count scaled-exp-str))
                                               \0)))
                                scaled-exp-str)
            exp-width (count scaled-exp-str)
            base-mantissa-width (count mantissa)
            scaled-mantissa (str (apply str (repeat (- k) \0))
                                 mantissa
                                 (if d
                                   (apply str
                                          (repeat
                                            (- d (dec base-mantissa-width)
                                               (if (neg? k) (- k) 0)) \0))))
            w-mantissa (if w (- w exp-width))
            [rounded-mantissa _ incr-exp] (round-str
                                            scaled-mantissa 0
                                            (cond
                                              (= k 0) (dec d)
                                              (pos? k) d
                                              (neg? k) (dec d))
                                            (if w-mantissa
                                              (- w-mantissa (if add-sign 1 0))))
            full-mantissa (insert-scaled-decimal rounded-mantissa k)
            append-zero (and (= k (count rounded-mantissa)) (nil? d))]
        (if (not incr-exp)
          (if w
            (let [len (+ (count full-mantissa) exp-width)
                  signed-len (if add-sign (inc len) len)
                  prepend-zero (and prepend-zero (not (= signed-len w)))
                  full-len (if prepend-zero (inc signed-len) signed-len)
                  append-zero (and append-zero (< full-len w))]
              (if (and (or (> full-len w) (and e (> (- exp-width 2) e)))
                       (:overflowchar params))
                (print (apply str (repeat w (:overflowchar params))))  ;;TODO print to *out*
                (print (str   ;;TODO print to *out*
                         (apply str
                                (repeat
                                  (- w full-len (if append-zero 1 0))
                                  (:padchar params)))
                         (if add-sign (if (neg? arg) \- \+))
                         (if prepend-zero "0")
                         full-mantissa
                         (if append-zero "0")
                         scaled-exp-str))))
            (print (str   ;;TODO print to *out*
                     (if add-sign (if (neg? arg) \- \+))
                     (if prepend-zero "0")
                     full-mantissa
                     (if append-zero "0")
                     scaled-exp-str)))
          (recur [rounded-mantissa (inc exp)]))))
    navigator))

;; the function to render ~G directives
;; This just figures out whether to pass the request off to ~F or ~E based
;; on the algorithm in CLtL.
;; TODO: support rationals. Back off to ~D/~A in the appropriate cases
;; TODO: refactor so that float-parts isn't called twice
(defn- general-float [params navigator offsets]
  (let [[arg _] (next-arg navigator)
        arg (convert-ratio arg)
        [mantissa exp] (float-parts (if (neg? arg) (- arg) arg))
        w (:w params)
        d (:d params)
        e (:e params)
        n (if (= arg 0.0) 0 (inc exp))
        ee (if e (+ e 2) 4)
        ww (if w (= w ee))
        d (if d d (max (count mantissa) (min n 7)))
        dd (- d n)]
    (if (<= 0 dd d)
      (let [navigator (fixed-float {:w ww, :d dd, :k 0,
                                    :overflowchar (:overflowchar params),
                                    :padchar (:padchar params), :at (:at params)}
                                   navigator offsets)]
        (print (apply str (repeat ee \space)))   ;;TODO print to *out*
        navigator)
      (exponential-float params navigator offsets))))

;; the function to render ~$ directives
;; TODO: support rationals. Back off to ~D/~A in the appropriate cases
(defn- dollar-float [params navigator offsets]
  (let [[arg navigator] (next-arg navigator)
        [mantissa exp] (float-parts (Math/abs arg))
        d (:d params)  ; digits after the decimal
        n (:n params)  ; minimum digits before the decimal
        w (:w params)  ; minimum field width
        add-sign (or (:at params) (neg? arg))
        [rounded-mantissa scaled-exp expanded] (round-str mantissa exp d nil)
        fixed-repr (get-fixed rounded-mantissa (if expanded (inc scaled-exp) scaled-exp) d)
        full-repr (str (apply str (repeat (- n (.indexOf fixed-repr \.)) \0)) fixed-repr)
        full-len (+ (count full-repr) (if add-sign 1 0))]
    (print (str    ;;TODO print to *out*
             (if (and (:colon params) add-sign) (if (neg? arg) \- \+))
             (apply str (repeat (- w full-len) (:padchar params)))
             (if (and (not (:colon params)) add-sign) (if (neg? arg) \- \+))
             full-repr))
    navigator))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for the '~[...~]' conditional construct in its
;; different flavors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ~[...~] without any modifiers chooses one of the clauses based on the param or
;; next argument
;; TODO check arg is positive int
(defn- choice-conditional [params arg-navigator offsets]
  (let [arg (:selector params)
        [arg navigator] (if arg [arg arg-navigator] (next-arg arg-navigator))
        clauses (:clauses params)
        clause (if (or (neg? arg) (>= arg (count clauses)))
                 (first (:else params))
                 (nth clauses arg))]
    (if clause
      (execute-sub-format clause navigator (:base-args params))
      navigator)))

;; ~:[...~] with the colon reads the next argument treating it as a truth value
(defn- boolean-conditional [params arg-navigator offsets]
  (let [[arg navigator] (next-arg arg-navigator)
        clauses (:clauses params)
        clause (if arg
                 (second clauses)
                 (first clauses))]
    (if clause
      (execute-sub-format clause navigator (:base-args params))
      navigator)))

;; ~@[...~] with the at sign executes the conditional if the next arg is not
;; nil/false without consuming the arg
(defn- check-arg-conditional [params arg-navigator offsets]
  (let [[arg navigator] (next-arg arg-navigator)
        clauses (:clauses params)
        clause (if arg (first clauses))]
    (if arg
      (if clause
        (execute-sub-format clause arg-navigator (:base-args params))
        arg-navigator)
      navigator)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Support for the '~{...~}' iteration construct in its
;; different flavors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; ~{...~} without any modifiers uses the next argument as an argument list that
;; is consumed by all the iterations
(defn- iterate-sublist [params navigator offsets]
  (let [max-count (:max-iterations params)
        param-clause (first (:clauses params))
        [clause navigator] (if (empty? param-clause)
                             (get-format-arg navigator)
                             [param-clause navigator])
        [arg-list navigator] (next-arg navigator)
        args (init-navigator arg-list)]
    (loop [count 0
           args args
           last-pos (int -1)]
      (if (and (not max-count) (= (:pos args) last-pos) (> count 1))
        ;; TODO get the offset in here and call format exception
        (throw (js/Error "%{ construct not consuming any arguments: Infinite loop!")))
      (if (or (and (empty? (:rest args))
                   (or (not (:colon (:right-params params))) (> count 0)))
              (and max-count (>= count max-count)))
        navigator
        (let [iter-result (execute-sub-format clause args (:base-args params))]
          (if (= :up-arrow (first iter-result))
            navigator
            (recur (inc count) iter-result (:pos args))))))))

;; ~:{...~} with the colon treats the next argument as a list of sublists. Each of the
;; sublists is used as the arglist for a single iteration.
(defn- iterate-list-of-sublists [params navigator offsets]
  (let [max-count (:max-iterations params)
        param-clause (first (:clauses params))
        [clause navigator] (if (empty? param-clause)
                             (get-format-arg navigator)
                             [param-clause navigator])
        [arg-list navigator] (next-arg navigator)]
    (loop [count 0
           arg-list arg-list]
      (if (or (and (empty? arg-list)
                   (or (not (:colon (:right-params params))) (> count 0)))
              (and max-count (>= count max-count)))
        navigator
        (let [iter-result (execute-sub-format
                            clause
                            (init-navigator (first arg-list))
                            (init-navigator (next arg-list)))]
          (if (= :colon-up-arrow (first iter-result))
            navigator
            (recur (inc count) (next arg-list))))))))

;; ~@{...~} with the at sign uses the main argument list as the arguments to the iterations
;; is consumed by all the iterations
(defn- iterate-main-list [params navigator offsets]
  (let [max-count (:max-iterations params)
        param-clause (first (:clauses params))
        [clause navigator] (if (empty? param-clause)
                             (get-format-arg navigator)
                             [param-clause navigator])]
    (loop [count 0
           navigator navigator
           last-pos (int -1)]
      (if (and (not max-count) (= (:pos navigator) last-pos) (> count 1))
        ;; TODO get the offset in here and call format exception
        (throw (js/Error "%@{ construct not consuming any arguments: Infinite loop!")))
      (if (or (and (empty? (:rest navigator))
                   (or (not (:colon (:right-params params))) (> count 0)))
              (and max-count (>= count max-count)))
        navigator
        (let [iter-result (execute-sub-format clause navigator (:base-args params))]
          (if (= :up-arrow (first iter-result))
            (second iter-result)
            (recur
              (inc count) iter-result (:pos navigator))))))))

;; ~@:{...~} with both colon and at sign uses the main argument list as a set of sublists, one
;; of which is consumed with each iteration
(defn- iterate-main-sublists [params navigator offsets]
  (let [max-count (:max-iterations params)
        param-clause (first (:clauses params))
        [clause navigator] (if (empty? param-clause)
                             (get-format-arg navigator)
                             [param-clause navigator])]
    (loop [count 0
           navigator navigator]
      (if (or (and (empty? (:rest navigator))
                   (or (not (:colon (:right-params params))) (> count 0)))
              (and max-count (>= count max-count)))
        navigator
        (let [[sublist navigator] (next-arg-or-nil navigator)
              iter-result (execute-sub-format clause (init-navigator sublist) navigator)]
          (if (= :colon-up-arrow (first iter-result))
            navigator
            (recur (inc count) navigator)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The '~< directive has two completely different meanings
;; in the '~<...~>' form it does justification, but with
;; ~<...~:>' it represents the logical block operation of the
;; pretty printer.
;;
;; Unfortunately, the current architecture decides what function
;; to call at form parsing time before the sub-clauses have been
;; folded, so it is left to run-time to make the decision.
;;
;; TODO: make it possible to make these decisions at compile-time.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(declare format-logical-block)
(declare justify-clauses)

(defn- logical-block-or-justify [params navigator offsets]
  (if (:colon (:right-params params))
    (format-logical-block params navigator offsets)
    (justify-clauses params navigator offsets)))

;;======================================================================
;; dispatch.clj
;;======================================================================

(defn- use-method
  "Installs a function as a new method of multimethod associated with dispatch-value. "
  [multifn dispatch-val func]
  (-add-method multifn dispatch-val func))

;;======================================================================
;; Dispatch for the basic data types when interpreted
;; as data (as opposed to code).
;;======================================================================

;;; TODO: inline these formatter statements into funcs so that we
;;; are a little easier on the stack. (Or, do "real" compilation, a
;;; la Common Lisp)

(defn- pprint-list [alis]
  (pprint-logical-block :prefix "(" :suffix ")"
    (print-length-loop [alis (seq alis)]
      (when alis
        (write-out (first alis))
        (when (next alis)
          (-write *out* " ")
          (pprint-newline :linear)
          (recur (next alis)))))))

;;; (def pprint-vector (formatter-out "~<[~;~@{~w~^ ~_~}~;]~:>"))
(defn- pprint-vector [avec]
  (pprint-logical-block :prefix "[" :suffix "]"
    (print-length-loop [aseq (seq avec)]
      (when aseq
        (write-out (first aseq))
        (when (next aseq)
          (-write *out* " ")
          (pprint-newline :linear)
          (recur (next aseq)))))))

;;; (def pprint-map (formatter-out "~<{~;~@{~<~w~^ ~_~w~:>~^, ~_~}~;}~:>"))
(defn- pprint-map [amap]
  (pprint-logical-block :prefix "{" :suffix "}"
    (print-length-loop [aseq (seq amap)]
      (when aseq
        ;;compiler gets confused with nested macro if it isn't namespaced
        ;;it tries to use clojure.pprint/pprint-logical-block for some reason
        (m/pprint-logical-block
          (write-out (ffirst aseq))
          (-write *out* " ")
          (pprint-newline :linear)
          (set! *current-length* 0)   ;always print both parts of the [k v] pair
          (write-out (fnext (first aseq))))
        (when (next aseq)
          (-write *out* ", ")
          (pprint-newline :linear)
          (recur (next aseq)))))))

(defn- pprint-simple-default [obj]
  ;;TODO: Update to handle arrays (?) and suppressing namespaces
  (-write *out* (pr-str obj)))

(defmulti simple-dispatch
          "The pretty print dispatch function for simple data structure format."
          (fn [obj]
            (cond
              (list? obj) :list
              (map? obj) :map
              (vector? obj) :vector
              (nil? obj) nil
              :default :default)))

(use-method simple-dispatch :list pprint-list)
(use-method simple-dispatch :vector pprint-vector)
(use-method simple-dispatch :map pprint-map)
(use-method simple-dispatch nil #(-write *out* (pr-str nil)))
(use-method simple-dispatch :default pprint-simple-default)

(set-pprint-dispatch simple-dispatch)

;;======================================================================
;; print_table.clj
;;======================================================================

