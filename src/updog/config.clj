(ns updog.config
  (:refer-clojure :exclude [read])
  (:require
    [clojure.edn :as edn]
    [clojure.spec.alpha :as s]
    [medley.core :as m]
    [updog.fs :as fs]
    [updog.github :as gh]))

(defn read
  [fname]
  (edn/read-string (slurp fname)))

(def writeable-dir? (comp fs/writeable-dir? fs/expand-home))

;; TODO Add generators for all relevant specs below
(s/def ::infer #{::infer})

(s/def ::source #{:github-release})
(s/def ::asset (s/or :infer ::infer
                     :asset  string?
                     :assets (s/coll-of string?)))
(s/def ::install-dir (s/or :dir (s/and string? writeable-dir?)
                           :infer ::infer))
(s/def ::install-files (s/or :all ::infer
                             :files (s/coll-of string?)))
(def ^:private rx-digits #"^[0-7]+$")
(def ^:private rx-mode #"[ugoa]*([-+=]([rwxXst]*|[ugo]))+|[-+=][0-7]+") ; From chmod(1)
(s/def ::chmod (s/nilable nat-int?))
(s/def ::archive-dir (s/nilable (s/and string? writeable-dir?)))

(s/def ::app (s/keys :req-un [::source ::asset ::install-dir ::install-files]
                     :opt-un [::gh/repo-slug ::chmod ::archive-dir]))

(comment
  (s/explain ::app
             {:app-key :walterl/updog
              :source :github-releasex
              :asset [""]
              :install-dir ""
              :install-files ::infer
              :repo-slug "foo/bar"
              :chmod 0750
              :archive-dir ""})
  ,)

(def default
  {:source        :github-release
   :asset         ::infer
   :install-dir   ::infer
   :install-files ::infer
   :chmod         0750})

(defn- wrap-vec-str
  [x]
  (if (string? x) [x] x))

(defn- maybe-find-install-dir
  [dir]
  (if (= ::infer dir)
    (let [write-dir (first (filter fs/writeable-dir? (fs/sys-path-dirs)))]
      (println (str "[WARNING] Using install directory from $PATH: " write-dir))
      write-dir)
    dir))

(defn- ensure-dir-writable
  [dir]
  (when-not (writeable-dir? dir)
    (throw (ex-info (str "Not a writable directory: " dir)
                    {:type ::invalid-dir, :dir dir}))))

(defn- prep-install-dir
  [dir]
  (-> dir
      (maybe-find-install-dir)
      (doto (ensure-dir-writable))))

(comment
  (def ^:private app-key :user/repo-name))

(defn- prep-repo-slug
  [repo-slug app-key]
  (if (nil? repo-slug)
    (str (namespace app-key) "/" (name app-key))
    repo-slug))

(defn- validate-app-conformity
  [config]
  (when-not (s/valid? ::app config)
    (throw (ex-info "Invalid app config" {:type ::invalid-app-config
                                          :config config
                                          :explain-data (s/explain-data ::app config)}))))

(defn- expand-home
  [s]
  (if (string? s)
    (fs/expand-home s)
    s))

(defn app-prep
  "Prepare app config by merging in default config and inferring possible
  values."
  [config app-key]
  (-> (merge default config)
      (assoc :app-key app-key)

      (m/update-existing :install-dir expand-home)
      (m/update-existing :archive-dir expand-home)

      (doto (validate-app-conformity))

      (update :asset wrap-vec-str)
      (update :install-dir prep-install-dir)
      (update :repo-slug prep-repo-slug app-key)
      (doto (comp ensure-dir-writable :archive-dir))

      (doto (validate-app-conformity))))

(comment
  (Throwable->map *e)
  (app-prep {} :walterl/updog)
  ,)
