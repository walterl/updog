(defproject updog "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[com.taoensso/timbre "4.10.0"]
                 [integrant "0.8.0"]
                 [integrant/repl "0.3.1"]
                 [org.clojure/clojure "1.10.1"]]
  :main ^:skip-aot updog.core
  :target-path "target/%s"

  :profiles
  {:dev
   {:source-paths   ["dev/src"]
    :resource-paths ["dev/resources"]}

   :repl {:repl-options {:init-ns user}}

   :uberjar {:aot :all}})
