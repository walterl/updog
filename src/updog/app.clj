(ns updog.app
  (:require
    [clojure.edn :as edn]
    [clojure.java.shell :refer [sh]]
    [clojure.pprint :refer [pprint]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [taoensso.timbre :as log]
    [updog.archive :as archive]
    [updog.config :as config]
    [updog.fs :as fs]
    [updog.github :as gh]
    [updog.net :as net])
  (:import
    [java.time ZonedDateTime]
    [java.time.format DateTimeFormatter]))

(defn- data-dir
  []
  (fs/path (or (System/getenv "XDG_DATA_HOME") "~/.local/share") "updog"))

(def ^:dynamic *update-log-filename* nil)

(defonce custom-log-filename (atom nil))

(defn- update-log-filename
  []
  (fs/expand-home
    (or *update-log-filename*
        @custom-log-filename
        (fs/path (data-dir) "update-log.edn"))))

(defn- read-update-log
  ([]
   (read-update-log (update-log-filename)))
  ([fname]
   (fs/ensure-dir! (fs/parent fname))
   (if (fs/exists? fname)
     (edn/read-string (slurp fname))
     [])))

(defn- latest-entry
  [update-log]
  (->> update-log
       (map #(assoc % :unix-timestamp (.toEpochSecond (ZonedDateTime/parse (:timestamp %)))))
       (sort-by :unix-timestamp)
       (last)))

(defn- now
  []
  (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME))

(defn- log-entry
  [upd config]
  (merge
    (select-keys upd [:installed-version :installed-files :download-url :downloaded-to])
    (select-keys config [:app-key :archive-dir])
    {:event (:status upd)
     :timestamp (now)}))

(defn- log-update!
  "Record `upd`ate in update log file."
  [upd config]
  (spit
    (update-log-filename)
    (with-out-str (pprint (conj (read-update-log) (log-entry upd config))))))

(defn- existing-command-candidates
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

(defn- previously-installed-files
  ([config]
   (previously-installed-files config (read-update-log)))
  ([{:keys [app-key]} update-log]
   (some->> update-log
            (filter #(and (= app-key (:app-key %)) (contains? % :installed-files)))
            (latest-entry)
            :installed-files)))

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
                         (#{::app-updated ::already-up-to-date} (:event %))))
           (latest-entry)
           :installed-version))

(defn installed-version
  ([config]
   (installed-version config (read-update-log)))
  ([{:keys [app-key] :as config} update-log]
   (or (last-installed-version app-key update-log)
       (when-let [cmd (first (previously-installed-files config))]
         (cmd-version cmd))
       (when-let [cmd (first (existing-command-candidates config))]
         (cmd-version cmd)))))

(defn latest-version
  [{:keys [repo-slug] :as _config}]
  (or (:tag-name (gh/fetch-release-version repo-slug :latest))
      "0"))

(defn- requires-update?
  [config installed-version latest-version]
  (log/debug "Installed version:" installed-version "| Latest version:" latest-version)
  (or (and (empty? (existing-command-candidates config))
           (empty? (filter fs/exists? (previously-installed-files config))))
      (nil? installed-version)
      ;; If the latest published version is different than the one we have, we
      ;; should probably change to it, regardless of whether it's semantically
      ;; newer or not. This is to account for accidental or retracted releases.
      ;; I.e. trust what upstream says is the latest, and use that.
      (not= installed-version latest-version)))

(comment
  (def asset (:asset config))
  (def app-key (:app-key config))
  (def repo-slug (:repo-slug config))
  )

(def ^:private archive-extensions
  #{".zip" ".gz" ".bz2" ".jar" ".tar"})

(defn- trim-extensions
  [fname]
  (if-let [ext (first (filter #(str/ends-with? fname %) archive-extensions))]
    (trim-extensions (subs fname 0 (- (count fname) (count ext))))
    fname))

(defn- split-asset-name
  [asset-name]
  (-> asset-name
      (trim-extensions)
      (str/split
        (re-pattern
          (if (< (count (filter #{\-} asset-name))
                 (count (filter #{\_} asset-name)))
            "_" "-")))))

(defn- asset-name-has?
  [{asset-name :name} name-part]
  (nat-int? (.indexOf (split-asset-name asset-name) name-part)))

(defn- most-applicable
  [assets app-key]
  (if (= 1 (count assets))
    assets
    (let [platform (str/lower-case (System/getProperty "os.name"))
          arch (System/getProperty "os.arch")
          ;; Filter for assets with names that start with app key
          assets (or (seq (filter #(re-matches
                                     (re-pattern (str "^" (name app-key) "\\b.*"))
                                     (:name %))
                                  assets))
                     assets)
          ;; Filter for assets with a platform tag
          assets (or (seq (filter #(asset-name-has? % platform) assets))
                     assets)
          ;; Filter for assets with an architecture tag
          assets (or (seq (filter #(asset-name-has? % arch) assets))
                     assets)
          ;; Filter for assets with an alternative architecture tag
          assets (if (= "amd64" arch)
                   ; Sometimes "x86_64" is used rather than "amd64"
                   (or (seq (filter #(asset-name-has? % "x86_64") assets))
                       assets)
                   assets)
          ;; Prefer static assets
          assets (or (seq (filter #(asset-name-has? % "static") assets))
                     assets)]
      (vec assets))))

(comment
  (map :name assets)
  (map :split-name assets)
  (filter #(contains? (:split-name %) platform) assets)
  (.indexOf ["clojure" "lsp" "native" "linux" "amd64.zip"] "linuxx")
  (nat-int? -1)
  ,)

(defn- latest-release-assets
  [{:keys [app-key asset repo-slug]}]
  (let [assets (gh/fetch-release-assets repo-slug)]
    (if (= ::config/infer asset)
      (most-applicable assets app-key)
      (for [asset-substr asset
            {asset-name :name, :as asset} assets
            :when (str/includes? asset-name asset-substr)]
        asset))))

(comment
  (def rassets (vec *1))
  (def platform (str/lower-case (System/getProperty "os.name")))
  (def arch (System/getProperty "os.arch"))
  (seq (System/getProperties))
  (System/getProperty "line.separator")
  (let [combinations #{(str platform "-" arch) (str arch "-" platform)}]
    (->> rassets
         (filter (fn [a] (some #(str/includes? (:name a) %) combinations)))
         (filter (fn [a] (#{".zip" ".bz2" ".gz"} (fs/extension (:name a)))))
         (map :name)))

  (fs/extension "foo.tar.bz2")

  (filter
    #(re-matches (re-pattern (str ".*\\b" platform "\\b.*")) (:name %))
    rassets)
  ,)

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
  [{:keys [app-key archive-dir] :as config}]
  (log/info "⚙️  Updating app" app-key)
  (log/debug "Update app config:" (with-out-str (pprint config)))
  (let [{:keys [download-url tag-name], asset-name :name} (first (latest-release-assets config))
        dl-dest (net/download-file download-url (asset-filename asset-name))
        installed-files (vec (install-asset dl-dest config))]
    (archive-downloaded! archive-dir dl-dest)
    {:status ::app-updated
     :installed-version tag-name
     :installed-files installed-files
     :download-url download-url
     :downloaded-to dl-dest}))

(defn process
  [config]
  (s/assert ::config/app config)
  (log/debug "Processing app with config:" (with-out-str (pprint config)))
  (let [installed (installed-version config)
        latest (latest-version config)
        result (if (requires-update? config installed latest)
                 (try
                   (update! config)
                   (catch Exception e
                     {:status ::unexpected-error
                      :installed-version installed
                      :latest-version latest
                      :error e}))
                 {:status ::already-up-to-date
                  :installed-version installed
                  :latest-version latest})]
    (log-update! result config)
    result))

(comment
  (def app-key updog.main/app-key)
  (def config updog.main/app-config)

  (def config (config/app-prep {} :walterl/updog))
  (def config (config/app-prep
                {:asset "linux-amd64.zip.sha256"
                 :install-dir "~/tmp/updog-test/bin"}
                :clj-kondo/clj-kondo))

  (def upd (process config))
  *e
  ,)
