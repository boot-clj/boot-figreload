(set-env!
 :source-paths #{"src"}
 :dependencies '[[org.clojure/clojure "1.8.0"     :scope "provided"]
                 [figwheel "0.5.9-SNAPSHOT"]
                 ;; AR - they are not required here because they live in the
                 ;; pod, but they are still useful for the repl
                 ;; [figwheel-sidecar "0.5.9-SNAPSHOT" :scope "test"]
                 ;; [http-kit            "2.1.19"    :scope "test"]
                 [adzerk/bootlaces    "0.1.13"    :scope "test"]
                 [adzerk/boot-test    "1.1.0"     :scope "test"]])

(require '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer [bootlaces! build-jar push-snapshot push-release]])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom {:project     'powerlaces/boot-figreload
      :version     +version+
      :description "Boot task to automatically reload page resources in the browser (featuring Figwheel)."
      :url         "https://github.com/boot-clj/boot-figreload"
      :scm         {:url "git@github.com:boot-clj/boot-figreload.git"}
      :license     {"Eclipse Public License"
                    "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask build []
  (comp
   (pom)
   (jar)
   (install)))

(deftask dev []
  (comp
   (watch)
   (repl :server true)
   (build)
   (target)))

(def snapshot? #(.endsWith +version+ "-SNAPSHOT"))

(deftask deploy []
  (comp
   (build-jar)
   (if (snapshot?)
     (push-snapshot)
     (push-release))))

(deftask run-tests []
  ;; AR - no tests for now
  ;; (test :namespaces #{})
  )
