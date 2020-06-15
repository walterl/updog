(ns user)

(defn dev
  "Load and switch to 'dev' namespace."
  []
  (require 'dev)
  (in-ns 'dev)
  :loaded)
