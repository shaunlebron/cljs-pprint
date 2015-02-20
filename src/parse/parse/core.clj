(ns parse.core
  (:require
    [clojure.java.io :as io]
    [clojure.tools.reader :as reader]
    [clojure.tools.reader.reader-types :as rt]
    [clojure.string :refer [split-lines join]]
    [clojure.pprint :refer [pprint]]
    [fipp.edn :refer [pprint] :rename {pprint fipp}]
    )
  )

(def output-name "resources/report/forms.edn")

;;------------------------------------------------------------
;; Filename utilities
;;------------------------------------------------------------

(defn get-filenames
  [dir exts]
  (let [files (file-seq (clojure.java.io/file dir))
        filenames (map #(.getPath %) files)
        validate (fn [f] (when (some #(.endsWith f %) exts) f))]
    (keep validate filenames)))

(defn isolate-filename
  [full-path]
  (let [i (.lastIndexOf full-path "/")]
    (if (>= i 0)
      (subs full-path (inc i))
      i)))

;;------------------------------------------------------------
;; Form Retrieving
;;------------------------------------------------------------

(defn get-forms-from-file
  [path]
  (let [is (clojure.java.io/input-stream path)
        r1 (rt/input-stream-push-back-reader is)
        r  (rt/source-logging-push-back-reader r1 1 path)]
    (loop [forms (transient [])]
      (if-let [f (try (reader/read r)
                      (catch Exception e
                        (when-not (= (.getMessage e) "EOF") (throw e))))]
        (recur (conj! forms f))
        (persistent! forms)))))

(defn get-forms-from-files
  [paths]
  (mapcat get-forms-from-file paths))

;;------------------------------------------------------------
;; Form Formatting
;;------------------------------------------------------------

(defn get-def-name
  [form]
  (let [[a b c] form
        aname (name a)]
    (when (and (.startsWith aname "def")
               (not= aname "defdirectives"))
      (if (= "defmethod" aname)
        [a b c]
        [a b]))))

(defn form-key
  [name-form]
  (let [[type- name- key-] name-form]
    (if key-
      (str name- " " key-)
      (str name-))))

(defn form-data
  [form]
  (let [m (meta form)
        name- (get-def-name form)
        num-lines (inc (- (:end-line m) (:line m)))]
    (if name-
      {;:form form
       :type (str (first name-))
       :key (form-key name-)
       :lines [(:line m) (:end-line m)]
       :filename (isolate-filename (:file m))
       :source (join "\n" (take-last num-lines (split-lines (:source m))))
       })))

;;------------------------------------------------------------
;; Form Storing
;;------------------------------------------------------------

(def clj-forms (atom {}))
(def cljs-forms (atom {}))

(defn make-form-map
  [atom- dir exts]
  (let [values (vec (keep form-data (get-forms-from-files (get-filenames dir exts))))
        names (map :key values)]
    (reset! atom- (zipmap names values))))

(defn make-all-form-maps
  []
  (make-form-map clj-forms "src/clj" [".clj"])
  (make-form-map cljs-forms "src/cljs/" [".clj" ".cljs"]))

(defn make-file-def-list
  [forms]
  (->> (keys forms)
       (sort-by #(get-in forms [% :lines 0]))
       (group-by #(get-in forms [% :filename]))))

;;------------------------------------------------------------
;; Form Displaying
;;------------------------------------------------------------

(defn show-form-map
  [atom-]
  (doseq [f (vals @atom-)]
    (println (:name f))
    (println (dissoc f :name :source :form))
    (println)
    (println (:source f))
    (println "\n=====================================\n")))

(defn show-all-form-maps
  []
  (println "CLJ FORMS:")
  (show-form-map clj-forms)

  (println "CLJS FORMS:")
  (show-form-map cljs-forms)
  )

;;------------------------------------------------------------
;; Program Entry
;;------------------------------------------------------------

(defn -main
  []
  (println "Retrieving forms...")
  (make-all-form-maps)
  (println (str "Writing forms to " output-name "..."))
  (spit output-name (with-out-str (fipp {:clj-files (make-file-def-list @clj-forms)
                                         :cljs-files (make-file-def-list @cljs-forms)
                                         :clj @clj-forms
                                         :cljs @cljs-forms})))
  (println "Done.")
  )
