(ns powerlaces.boot-figreload.util
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [boot.file :as file])
  (:import [java.io File]))

(defn build-id
  "Return the build id from the file path (as string)."
  [file-path]
  (str/replace file-path #"\.cljs\.edn$" ""))

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
