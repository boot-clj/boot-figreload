(ns powerlaces.boot-figreload.util-test
  (:require [powerlaces.boot-figreload.util :as util]
            [clojure.test :as test :refer [deftest is]]))

(deftest build-id
  (is (= (re-find #"^main-.*" (util/build-id "/my/project/src/my_namespace/main.cljs.edn"))) "Should correctly take the part before .cljs.edn as build-id")
  (is (= (re-find #"^transient$-.*" (util/build-id "/my/project/src/my_namespace/transient.cljs.edn"))) "Should correctly escape JS reserved words as build-id")
  (is (thrown? java.lang.AssertionError (util/build-id "transient.edn")) "Should throw if we are not passing a .cljs.edn file")
  (is (thrown? java.lang.AssertionError (util/build-id "transient.cljs.edn")) "Should throw if we are not passing a path"))
