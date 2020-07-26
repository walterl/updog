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
  replacing given vars. See tests for use cases.

  => (command->sh-args \"echo FOO_BAR\" {:foo-bar \"var value\"})
  \"echo var value\""
  ([command]
   (command->sh-args command {}))
  ([command vars]
   (-> command
       (replace-vars vars)
       (str/split #"\s+"))))

(defn copy!
  "Copy local file `src` to `dest`."
  [src dest]
  (log/debugf "Copy %s → %s" src dest)
  (with-open [in  (io/input-stream src)
              out (io/output-stream dest)]
    (io/copy in out)))

(defn download-file!
  "Download `url` to `dest`."
  [url dest]
  (log/debugf "Downloading %s → %s" url dest)
  (with-open [out (io/output-stream dest)]
    (io/copy (:body (http/get url {:as :stream}))
             out)))

(defn temp-file-path
  "Returns path to temp file."
  []
  (.getPath (fs/temp-file "updog_")))

(defn file-exists?
  "Test if `filename` exists."
  [filename]
  (.exists (io/file filename)))

(defmulti ->str
  "Creates string representation of argument."
  class)

(defmethod ->str :default
  [x]
  (str x))
