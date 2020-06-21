(ns updog.util
  (:require [clojure.string :as str]
            [me.raynes.fs :as fs]))

(defn- var-key->cmd-var-name
  [k]
  (-> (name k)
      (str/replace "-" "_")
      str/upper-case))

(defn- replace-vars
  [command vars]
  (reduce (fn [cmd [k v]]
            (str/replace cmd (var-key->cmd-var-name k) v))
          command
          vars))

(defn command->args
  [command vars]
  (-> command
      (replace-vars vars)
      (str/split #"\s+")))

(defn temp-file
  []
  (.getPath (fs/temp-file "updog_")))
