(ns updog.core
  (:require [clojure.pprint :refer [pprint]]
            [updog.cli :as cli])
  (:gen-class))

(defn- print-result
  [{:keys [output], :as m}]
  (cond
    (string? output) (println output)
    (some? output)   (pprint output)
    :else            (pprint m)))

(defn -main
  [& args]
  (print-result (cli/main args)))
