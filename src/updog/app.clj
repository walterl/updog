(ns updog.app
  (:require
    [clojure.java.shell :refer [sh]]
    [clojure.pprint :refer [pprint]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [updog.archive :as archive]
    [updog.config :as config]
    [updog.fs :as fs]
    [updog.github :as gh]
    [updog.net :as net])
  (:import clojure.lang.ExceptionInfo))

(defn- command-candidates
  [{:keys [install-dir install-files], app-key :key}]
  (->> (reduce into []
               [(when (sequential? install-files)
                  (map #(fs/file-name %) install-files))
                [(name app-key)
                 (namespace app-key)]])
       (map #(fs/path install-dir %))
       (filter fs/exists?)))

(defn cmd-version
  "Get version from running `cmd --version`."
  [cmd]
  (try
    (when (and (fs/exists? cmd) (fs/executable? cmd))
      (-> (sh cmd "--version") ; => {:out "clj-kondo v2022.08.03\n"}
          :out                 ; => "clj-kondo v2022.08.03\n"
          str/trim             ; => "clj-kondo v2022.08.03"
          (str/split #"\s+")   ; => ["clj-kondo" "v2022.08.03"]
          last))               ; => "v2022.08.03"
    (catch Throwable ex
      (printf "Failed to get version from `%s --version`\n" cmd)
      (pprint (Throwable->map ex))
      nil)))

(defn installed-version
  [config]
  (when-let [cmd (first (command-candidates config))]
    (cmd-version cmd)))

(defn latest-version
  [{:keys [repo-slug] :as _config}]
  (or (:tag-name (gh/fetch-release-version repo-slug :latest))
      "0"))

(defn- requires-update?
  [config]
  (let [installed (installed-version config)
        latest (latest-version config)]
    (println "Installed version:" installed)
    (println "Latest version:" installed)
    (or (empty? (command-candidates config))
        (nil? installed)
        ;; If the latest published version is different than the one we have, we
        ;; should probably change to it, regardless of whether it's semantically
        ;; newer or not. This is to account for accidental or retracted releases.
        ;; I.e. trust what upstream says is the latest, and use that.
        (not= installed latest))))

(defn- latest-version-asset-urls
  [{:keys [asset repo-slug]}]
  (for [asset-substr asset
        {:keys [label] :as url} (gh/fetch-release-asset-urls repo-slug)
        :when (str/includes? label asset-substr)]
    url))

(defn- install-asset-files
  [archive-filename label {:keys [install-dir install-files]}]
  (try
    (archive/extract archive-filename install-files install-dir)
    ;; Assume it's an executable file
    (catch ExceptionInfo e
      (if (= ::archive/unsupported-archive (-> e ex-data :type))
        (let [install-filename (fs/path install-dir label)]
          (fs/copy archive-filename install-filename)
          [install-filename])
        (throw e)))))

(defn- install-asset
  [dl-filename label config]
  (let [installed-files (install-asset-files dl-filename label config)]
    (fs/chmod-files installed-files (:chmod config))))

(defn- asset-filename
  [label]
  (fs/path (fs/temp-dir) label))

(defn update!
  [config]
  (println "Update!" (pr-str config))
  (let [{:keys [label download-url]} (first (latest-version-asset-urls config))
        asset-filename (asset-filename label)]
    (net/download-file download-url asset-filename)
    (install-asset asset-filename label config))
  ::app-updated)

(defn process
  [config]
  (s/assert ::config/app config)
  (if (requires-update? config)
    (update! config)
    ::already-up-to-date))
