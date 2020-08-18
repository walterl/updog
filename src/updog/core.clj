(ns updog.core
  (:require [updog.cli :as cli])
  (:gen-class))

(defn -main
  [& args]
  (cli/main args))
