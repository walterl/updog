(ns updog.config-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest is testing]]
    [updog.config :as cfg]
    [updog.fs :as fs])
  (:import clojure.lang.ExceptionInfo))

(deftest app-prep-tests
  (binding [fs/*sys-path-dirs* ["/tmp"]]
    (testing "empty config generates default config"
      (is (= {:source :github-release
              :asset [""]
              :install-dir "/tmp"
              :install-files :updog.config/infer
              :key :walterl/updog
              :repo-slug "walterl/updog"}
             (cfg/app-prep {} :walterl/updog))))
    (testing "fails on invalid input config"
      (try
        (cfg/app-prep {:source :gitlab} :walterl/updog)
        (is (nil? "cfg/app-prep above didn't throw :("))
        (catch ExceptionInfo e
          (let [{:keys [explain-data], ex-type :type} (ex-data e)]
            (is (= ::cfg/invalid-app-config
                   ex-type))
            (is (= {:path [:source]
                    :val :gitlab}
                   (-> explain-data ::s/problems first (select-keys [:path :val]))))))))
    (testing "home-expands directory"
      (doseq [config-key [:archive-dir :install-dir]]
        (testing (pr-str config-key)
          (try
            (cfg/app-prep {config-key "~"} :walterl/updog)
            (is (some? "didn't throw :)"))
            (catch ExceptionInfo e
              (is (nil? e)))))))))
