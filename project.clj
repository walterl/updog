(defproject updog "0.2.1-SNAPSHOT"
  :description "Your watchdog for updates of software without updates."
  :url "https://github.com/walterl/updog"
  :license {:name "GPLv3"
            :url "https://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [cheshire "5.10.0"]
                 [clj-commons/fs "1.6.307"]
                 [clj-http "3.10.1"]
                 [com.taoensso/timbre "5.1.0"]
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
