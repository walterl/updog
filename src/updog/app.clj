(ns updog.app
  (:require
    [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.pprint :refer [pprint]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [updog.archive :as archive]
    [updog.config :as config]
    [updog.fs :as fs]
    [updog.github :as gh]
    [updog.net :as net])
  (:import
    [clojure.lang ExceptionInfo]
    [java.time ZonedDateTime]
    [java.time.format DateTimeFormatter]))

(defn- data-dir
  []
  (fs/path
    (fs/expand-home (or (System/getenv "XDG_DATA_HOME") "~/.local/share"))
    "updog"))

(def ^:dynamic *update-log-filename* nil)

(defn- update-log-filename
  []
  (or *update-log-filename*
      (fs/path (data-dir) "update-log.edn")))

(defn- read-update-log
  ([]
   (read-update-log (update-log-filename)))
  ([fname]
   (fs/ensure-dir! (fs/parent fname))
   (if (fs/exists? fname)
     (edn/read-string (slurp fname))
     [])))

(defn- now
  []
  (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME))

(defn- log-entry
  [{:keys [config] :as upd}]
  (merge
    (select-keys upd [:installed-version :installed-files :asset-download-url :asset-downloaded-to])
    (select-keys config [:archive-dir])
    {:event (:status upd)
     :timestamp (now)
     :app-key (:app-key config)}))

(defn- log-update!
  "Record `upd`ate in update log file."
  [upd]
  (spit
    (update-log-filename)
    (with-out-str (pprint (conj (read-update-log) (log-entry upd))))))

(defn- command-candidates
  "Returns existing `install-files` in `install-dir`, including files named for
  `app-key`'s name or namespace."
  [{:keys [app-key install-dir install-files]}]
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

(comment
  (def app-key :clj-kondo/clj-kondo)
  (def update-log (read-update-log))
  ,)

(defn- last-installed-version
  [app-key update-log]
  (some->> update-log
           (filter #(and (= app-key (:app-key %))
                         (= ::app-updated (:event %))))
           (map #(assoc % :unix-timestamp (.toEpochSecond (ZonedDateTime/parse (:timestamp %)))))
           (sort-by :unix-timestamp)
           (last)
           :installed-version))

(defn installed-version
  ([config]
   (installed-version config (read-update-log)))
  ([{:keys [app-key] :as config} update-log]
   (or (last-installed-version app-key update-log)
       (when-let [cmd (first (command-candidates config))]
         (cmd-version cmd)))))

(defn latest-version
  [{:keys [repo-slug] :as _config}]
  (or (:tag-name (gh/fetch-release-version repo-slug :latest))
      "0"))

(defn- requires-update?
  [config]
  (let [installed (installed-version config)
        latest (latest-version config)]
    (println "Installed version:" installed)
    (println "Latest version:" latest)
    (or (empty? (command-candidates config))
        (nil? installed)
        ;; If the latest published version is different than the one we have, we
        ;; should probably change to it, regardless of whether it's semantically
        ;; newer or not. This is to account for accidental or retracted releases.
        ;; I.e. trust what upstream says is the latest, and use that.
        (not= installed latest))))

(comment
  (def asset (:asset config))
  (def app-key (:app-key config))
  (def repo-slug (:repo-slug config))
  )

(defn- latest-version-asset-urls
  [{:keys [asset repo-slug]}]
  (for [asset-substr asset
        {asset-name :name, :as url} (gh/fetch-release-assets repo-slug)
        :when (str/includes? asset-name asset-substr)]
    url))

(defn- install-asset-files
  "Extract `install-files` from `archive-filename` into `install-dir`.

  If `install-files` is `:updog.config/infer`, all executable files in the
  archive, or the largest file in the archive is used.

  Returns paths to extracted files."
  [archive-filename {:keys [install-dir install-files]}]
  (archive/extract
    archive-filename
    (if (= ::config/infer install-files)
      (or (seq (archive/list-executables archive-filename))
          (archive/largest-file archive-filename))
      install-files)
    install-dir))

(defn- install-asset
  [dl-filename config]
  (let [installed-files (install-asset-files dl-filename config)]
    (fs/chmod-files installed-files (:chmod config))
    installed-files))

(defn- asset-filename
  [label]
  (fs/path (fs/temp-dir) label))

(defn- archive-downloaded!
  [dir dl-dest]
  (when dir
    (let [dir* (fs/expand-home dir)
          arch-dest (fs/path dir* (fs/file-name dl-dest))]
      (fs/ensure-dir! dir*)
      (fs/copy dl-dest arch-dest))))

(defn update!
  [{:keys [archive-dir] :as config}]
  (println "Update!" (pr-str config))
  (let [{:keys [download-url tag-name], asset-name :name} (first (latest-version-asset-urls config))
        dl-dest (net/download-file download-url (asset-filename asset-name))
        installed-files (vec (install-asset dl-dest config))]
    (archive-downloaded! archive-dir dl-dest)
    {:status ::app-updated
     :config config
     :installed-version tag-name
     :installed-files installed-files
     :download-url download-url
     :downloaded-to dl-dest}))

(defn process
  [config]
  (s/assert ::config/app config)
  (let [result (if (requires-update? config)
                 (try
                   (update! config)
                   (catch Exception e
                     (println "Error!" e)
                     {:status ::unexpected-error, :error e}))
                 {:status ::already-up-to-date, :config config})]
    (log-update! result)
    result))

(comment
  (def config updog.main/prepped-config)
  (def config (config/app-prep {} :walterl/updog))
  (def config (config/app-prep
                {:asset "linux-amd64.zip.sha256"
                 :install-dir "~/tmp/updog-test/bin"}
                :clj-kondo/clj-kondo))
  (def upd (process config))
  *e
  ,)
