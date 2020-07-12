(ns updog.util
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as log]))

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

(defn command->sh-args
  "Prepare `command` for use as `args` vector for `clojure.java.shell/sh`,
  replacing given vars. See tests for use cases."
  ([command]
   (command->sh-args command {}))
  ([command vars]
   (-> command
       (replace-vars vars)
       (str/split #"\s+"))))

(defn download-file!
  [url dest]
  (log/debugf "Downloading %s → %s" url dest)
  (with-open [out (io/output-stream dest)]
    (io/copy (:body (http/get url {:as :stream}))
             out)))

(defn temp-file
  []
  (.getPath (fs/temp-file "updog_")))

(defmulti ->str
  "Creates string representation of argument."
  class)

(defmethod ->str :default
  [x]
  (str x))
