(ns updog.core
  (:require [clojure.pprint :refer [pprint]]
            [updog.cli :as cli])
  (:gen-class))

(defn- print-result
  [{:keys [error output], :as m}]
  (cond
    (string? output) (println output)
    (some? output)   (pprint output)
    :else            (pprint m))
  (when-let [error-msg (:msg error)]
    (println "Error:" error-msg)
    (System/exit 1)))

(defn -main
  [& args]
  (print-result (cli/main args)))
