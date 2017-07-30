(ns ^{:doc "Figwheel bindings and/or functions copied over."
      :author "Andrea Richiardi"}
    powerlaces.boot-figreload.figwheel
  (:require [boot.pod :as pod]
            [boot.util :as butil]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.java.io :as io]
            [figwheel-sidecar.config :as config]
            [figwheel-sidecar.build-middleware.javascript-reloading :as js-reload]
            [figwheel-sidecar.cljs-utils.exception-parsing :as ex-parsing]
            [figwheel-sidecar.build-middleware.injection :as injection]
            [cljs.compiler]
            [cljs.closure]
            [powerlaces.boot-figreload.util :as util]
            [powerlaces.boot-figreload.messages :as msgs]))

;;;;;;;;;;;;;;;;;;;;
;; DIRECT IMPORTS ;;
;;;;;;;;;;;;;;;;;;;;

(def ^{:doc "Generate the bootstrap namespace path string"
       :arglists '([build-config])}
  bootstrap-ns-name
  (var-get #'figwheel-sidecar.build-middleware.injection/figwheel-connect-ns-name))

(def ^{:doc "Generate the bootstrap namespace path string"
       :arglists '([build-config])}
  bootstrap-ns-path
  (var-get #'figwheel-sidecar.build-middleware.injection/figwheel-connect-ns-path))

(def ^{:doc "Generate the bootstrap namespace content"
       :arglists '([build-config])}
  bootstrap-ns-content
  (var-get #'figwheel-sidecar.build-middleware.injection/generate-connect-script))

(defn wrap-msg
  "Add common fields  to the message

  Note that msg keys always override in case of conflict."
  ([msg] (wrap-msg msg nil))
  ([msg opts]
   (-> opts
       (select-keys [:project-id :build-id])
       (assoc :figwheel-version config/_figwheel-version_)
       (merge msg)
       util/remove-nils)))

;; Reusing figwheel's make-sendable-file is not possible at the moment
;; https://github.com/bhauman/lein-figwheel/blob/e47da1658a716f83888e5a5164ee88e59b2d8c1e/sidecar/src/figwheel_sidecar/build_middleware/notifications.clj#L78
;; (intern *ns* 'make-sendable-file (var-get #'notifications/make-sendable-file))

(defn- client-path
  "Return the path that the client uses (relative to :asset-path)

  The input file map "
  [compile-opts file-map]
  (assert (:relative-path file-map) "The file-map is missing some fields, this is a bug.")
  (-> (str/replace (:relative-path file-map)
                   (or (:asset-path file-map) "")
                   "")
      (str/replace #"^/" "")))

(defn- guess-namespace
  [compile-opts file-map]
  (assert (:relative-path file-map) "The file-map is missing some fields, this is a bug.")
  (assert (:full-path file-map) "The file-map is missing some fields, this is a bug.")
  ;; Workaround for: https://github.com/bhauman/lein-figwheel/pull/537
  ;;
  ;; No figwheel-sidecar.build-middleware.javascript-reloading/guess-namespace
  ;; because deemed broken (sorry Bruce, it will be fixed I am sure).
  ;; We try to create the absolute path relative to the project root first.
  (let [js-file-attempts (conj (->> (util/project-dirs)
                                    (mapv #(io/file % (:relative-path file-map)))
                                    (filter #(.exists %))
                                    (mapv str))
                               (:full-path file-map))]
    (butil/dbug* "Guessing namespace for %s\n" (butil/pp-str js-file-attempts))
    (assert (every? #(re-find #"\.js$" %) js-file-attempts)
            "Cannot guess namespace for files that are not Javascript. This is an bug, please report it.")
    (->> js-file-attempts
         (mapv cljs.closure/parse-js-ns)
         first
         :provides
         first
         cljs.compiler/munge)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; :msg-name :files-changed ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- sendable-js-map
  "Make a (Javascript) file map sendable according to figwheel protocol.

  Mimicking the function to
  figwheel-sidecar.build-middleware.notifications/make-sendable-file

  Sample return map:
    {:file \"resources/public/js/compiled/out/test_fig/core.js\"
     :namespace \"test_fig.core\"
     :type :namespace}"
  [js-and-opts]
  {:namespace (guess-namespace (:cljs-opts js-and-opts) js-and-opts)
   :file (client-path (:cljs-opts js-and-opts) js-and-opts)
   :type :namespace})

(defn- sendable-css-map
  "Make a (Javascript) file map sendable according to figwheel protocol.

  Mimicking the function to
  figwheel-sidecar.build-middleware.notifications/make-sendable-file

  Sample return map:
    {:file \"resources/public/js/compiled/out/test_fig/core.js\"
     :namespace \"test_fig.core\"
     :type :namespace}"
  [js-and-opts]
  {:file (client-path (:cljs-opts js-and-opts) js-and-opts)
   :type :css})

(defmethod msgs/file-payload-by-extension :js
  [opts [ext change-maps]]
  (when (seq change-maps)
    (-> {:msg-name :files-changed
         :files (mapv sendable-js-map change-maps)
         :figwheel-meta {"figwheel.client.utils" {:figwheel-no-load true}}}
        (wrap-msg opts))))

(defmethod msgs/file-payload-by-extension :css
  [opts [ext change-maps]]
  (when (seq change-maps)
    (-> {:msg-name :css-files-changed
         :files (mapv sendable-css-map change-maps)}
        (wrap-msg opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; :msg-name :compile-warning ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; AR - TODO handle multiple warnings, now we return only one
(defmethod msgs/visual-payload-by-type :warnings
  [opts [type warning-maps]]
  (when (seq warning-maps)
    (-> {:msg-name :compile-warning
         :message (ex-parsing/parse-warning (first warning-maps))}
        (wrap-msg opts))))

(defn trim-source-paths
  "Remove any :source-paths or :resource-paths parent from path"
  [path]
  (->> (:source-paths pod/env)
       (filter #(str/index-of path %))
       (map #(-> path
                 (str/replace % "")
                 (str/replace-first #"^\/" "")))))

(defn- clean-class-form
  [form]
  (update form 1 #(-> % symbol resolve)))

(defn- relativize-data-form
  "Replace absolute paths with relative ones"
  [relative-path form]
  (let [file (get-in form [1 :file])]
    (cond
      (or (not form)
          (not (string? relative-path))
          (not (string? file))) form

      ;; We replace only if it is a substring of the original
      (some (partial str/includes? file) (trim-source-paths relative-path))
      (assoc-in form [1 :file] relative-path)

      :else form)))

(defn- relativize-cause-form
  "Replace absolute paths with relative ones where necessary"
  [relative-path form]
  (let [file (get-in form [1 :data :file])
        message (get-in form [1 :message])]
    (cond
      (or (not form)
          (not (string? file))
          (not (string? message))) form

      ;; We replace only if it is there is a substring in the original msg
      (str/includes? message file)
      (assoc-in form [1 :message] (-> message
                                      (str/replace file relative-path)
                                      (str/replace "file:" "")))

      :else form)))

(defn figwheelify-exception
  "Boot-cljs to figwheel exception map

  Just to make it super clear, we receive a serialized exception map
  from boot-cljs and we want to convert it to a format that figwheel can
  parse."
  [ex]
  (let [relative-file (when (= :boot-cljs (get-in ex [:data :from]))
                        (get-in ex [:data :file]))]
    (walk/prewalk #(cond
                     ;; Convert string in :class to a java.lang.Class obj
                     (util/map-entry-with-key? % :class) (clean-class-form %)
                     ;; Make all the :data :file entries relative
                     ;; I assume (!) the first exception contains the relative
                     ;; path
                     (and relative-file (util/map-entry-with-key? % :cause)) (relativize-cause-form relative-file %)
                     (and relative-file (util/map-entry-with-key? % :data)) (relativize-data-form relative-file %)
                     :else %)
                  ex)))

(defmethod msgs/visual-payload-by-type :exception
  [opts [type ex-map]]
  ;; AR - this is real hammering
  (-> {:msg-name :compile-failed
       :exception-data (-> ex-map
                           figwheelify-exception
                           (ex-parsing/parse-inspected-exception opts))}
      (wrap-msg opts)))

(comment
  (def ex-map {:class "clojure.lang.ExceptionInfo", :message "Parameter declaration \".info\" should be a vector at line 10, column 1 in file src/figreload_demo/core.cljs\n", :data {:file "src/figreload_demo/core.cljs", :line 10, :column 1, :tag :cljs/analysis-error, :from :boot-cljs, :boot.util/omit-stacktrace? true}, :cause {:class "clojure.lang.ExceptionInfo", :message "failed compiling file:/home/arichiardi/.boot/cache/tmp/home/arichiardi/git/figreload-demo/mbk/7of19k/figreload_demo/core.cljs", :data {:file "/home/arichiardi/.boot/cache/tmp/home/arichiardi/git/figreload-demo/mbk/7of19k/figreload_demo/core.cljs"}, :cause {:class "clojure.lang.ExceptionInfo", :message "Parameter declaration \".info\" should be a vector at line 10 /home/arichiardi/.boot/cache/tmp/home/arichiardi/git/figreload-demo/mbk/7of19k/figreload_demo/core.cljs", :data {:file "/home/arichiardi/.boot/cache/tmp/home/arichiardi/git/figreload-demo/mbk/7of19k/figreload_demo/core.cljs", :line 10, :column 1, :tag :cljs/analysis-error}, :cause {:class "java.lang.IllegalArgumentException", :message "Parameter declaration \".info\" should be a vector", :data nil, :cause nil}}}})
  )
