(ns updog.util
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clj-http.client :as http]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as fs.comp]
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
  {:pre [(not-empty url)]}
  (log/debugf "Downloading %s → %s" url dest)
  (with-open [out (io/output-stream dest)]
    (io/copy (:body (http/get url {:as :stream}))
             out)))

(defn tmp-dir
  "Returns path to new temp dir."
  []
  (.getPath (fs/temp-dir "updog-")))

(defmulti ->str
  "Creates string representation of argument."
  class)

(defmethod ->str :default
  [x]
  (str x))

(defn assoc-f
  "Performs steps on a map `m`, recording the results of each step in the map, so that the results are available to the
  next step.

  => (assoc-f {}
              :foo (constantly 1)
              :bar (comp inc :foo)
              :baz (comp inc inc :bar))
  {:foo 1, :bar 2, :baz 4}"
  [m & args]
  {:pre [(even? (count args))]}
  (reduce (fn [m [k f]] (assoc m k (f m)))
          m
          (partition 2 args)))

(defn chmod+x
  [path]
  (log/debugf "chmod u+x %s" path)
  (fs/chmod "u+x" path))

(defn shell-script
  [shell-script vars]
  (when-not shell-script
    (throw (ex-info "Shell script missing" {:type ::missing-shell-script})))
  (log/debugf "shell: %s" (command->sh-args shell-script vars))
  (apply sh (command->sh-args shell-script vars)))

(defn unzip
  [zip-path dest-path]
  (when-not (fs/exists? dest-path)
    (log/debugf "mkdir -p %s" dest-path)
    (fs/mkdirs dest-path))
  (log/debugf "unzip %s %s" zip-path dest-path)
  (fs.comp/unzip zip-path dest-path))

(defn temp-sub-dir
  [base-dir prefix]
  (let [sub-dir (fs/temp-name (str base-dir "/" prefix))]
    (fs/mkdirs sub-dir)
    sub-dir))

(defn dir-files
  [path]
  (->> (fs/walk (fn [root _ files] (map #(str root "/" %) files))
                path)
       (mapcat identity)))

(defn unzipped-files
  ([zip-path tmp-dir]
   (unzipped-files zip-path tmp-dir "unzip-"))
  ([zip-path tmp-dir tmp-prefix]
   (let [unzip-dir (temp-sub-dir tmp-dir tmp-prefix)]
     (unzip zip-path unzip-dir)
     (dir-files unzip-dir))))

(defn cmd-version
  "Get bin version from running `cmd --version`."
  [cmd]
  (try
    (when (fs/exists? cmd)
      (-> (sh cmd "--version")
          :out
          str/trim
          (str/split #"\s+")
          last))
    (catch Throwable ex
      (log/errorf ex "Failed to get version from `%s --version`" cmd)
      nil)))
