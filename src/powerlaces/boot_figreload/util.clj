(ns powerlaces.boot-figreload.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [boot.file :as file]
            [boot.from.digest :as digest])
  (:import [java.io File]))

;; From cljs/analyzer.cljc
(def js-reserved
  #{"arguments" "abstract" "boolean" "break" "byte" "case"
    "catch" "char" "class" "const" "continue"
    "debugger" "default" "delete" "do" "double"
    "else" "enum" "export" "extends" "final"
    "finally" "float" "for" "function" "goto" "if"
    "implements" "import" "in" "instanceof" "int"
    "interface" "let" "long" "native" "new"
    "package" "private" "protected" "public"
    "return" "short" "static" "super" "switch"
    "synchronized" "this" "throw" "throws"
    "transient" "try" "typeof" "var" "void"
    "volatile" "while" "with" "yield" "methods"
    "null" "constructor"})

(defn build-id
  "Return the build id from the file path (as string)."
  [cljs-edn-path]
  (assert (and (re-find #"\.cljs\.edn$" cljs-edn-path)
               (re-find #"(\/|\\)" cljs-edn-path))
          (format "Build-id expects a .cljs.edn file path. Received %s, this might be a bug." cljs-edn-path))
  (let [bid (str/replace cljs-edn-path #"(.*)(\/|\\)(\w+)(\.cljs\.edn)$" "$3")]
    (cond-> bid
      (js-reserved bid) (str "$")
      true (str "_" (->> cljs-edn-path digest/sha-1 (take 8) (str/join))))))

;;
;; Exception serialization
;; Also see: https://github.com/boot-clj/boot/issues/553
;;

(defn safe-data [data]
  (walk/postwalk
   (fn [x]
     (cond
       (instance? File x) (.getPath x)
       :else x))
   data))

(defn serialize-exception
  "Serializes given exception keeping original message, stack-trace, cause stack
   and ex-data for ExceptionInfo.

   Certain types in ex-data are converted to strings. Currently this includes
   Files."
  [e]
  {:class (-> e type .getName) ;; AR - this is ignored by the deserializer
   :message (.getMessage e)
   :data (safe-data (ex-data e)) ;; AR - no :ex-data, figwheel likes :data
   :cause (when-let [cause (.getCause e)]
            (serialize-exception cause))})

(defn remove-nils [m]
  (let [f (fn [x]
            (if (map? x)
              (let [kvs (filter (comp not nil? second) x)]
                (if (empty? kvs) nil (into {} kvs)))
              x))]
    (walk/postwalk f m)))

(defn map-entry-with-key?
  [form k]
  (and (vector? form) (= k (first form))))
