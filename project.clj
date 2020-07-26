(defproject updog "0.1.0-SNAPSHOT"
  :description "Your watchdog for updates of software without updates."
  :url "https://github.com/walterl/updog"
  :license {:name "GPLv3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire "5.10.0"]
                 [clj-commons/fs "1.5.2"]
                 [clj-http "3.10.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [integrant "0.8.0"]
                 [integrant/repl "0.3.1"]
                 [medley "1.3.0"]]
  :main ^:skip-aot updog.core
  :target-path "target/%s"

  :profiles
  {:dev
   {:source-paths   ["dev/src"]
    :resource-paths ["dev/resources"]}

   :repl {:repl-options {:init-ns user}}

   :uberjar {:aot :all}})
