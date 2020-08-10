(ns updog.app-source.github-release
  "GitHub releases app source"
  (:require [cheshire.core :as json]
            [integrant.core :as ig]
            [taoensso.timbre :as log]
            [updog.app-source :refer [AppSource source-type]]
            [updog.util :as u]))

(def ^:private github-latest-release-url
  "https://api.github.com/repos/%s/releases/latest")

(defn- asset-matcher
  [selector asset]
  (if selector
    (re-seq (re-pattern selector) (:name asset))
    asset))

(defn- tag->version
  [version-regex version-tag]
  (if version-regex
    (let [regex (re-pattern (format "^.*?(%s).*?$" version-regex))]
      (last (re-matches regex version-tag)))
    version-tag))

(defrecord GithubReleaseSource []
  AppSource
  (source-type [_] :github-release)

  (fetch-latest-version-data!
    [_ {:keys [asset-selector version-regex] :as app}]
    (log/debug ::fetch-latest-version-data! app)
    (let [repo           (get-in app [:github :repo])
          url            (format github-latest-release-url repo)
          latest-version (-> (slurp url) (json/parse-string true))
          asset          (some (partial asset-matcher asset-selector)
                               (:assets latest-version))]
      {:version  (tag->version version-regex (:tag_name latest-version))
       :filename (:name asset)
       :location (:browser_download_url asset)}))

  (download!
    [_ {:keys [latest-version tmp-dir] :as app}]
    (let [tmp-path (str tmp-dir "/" (:filename latest-version))]
      (u/download-file! (:location latest-version) tmp-path)
      tmp-path)))

(defmethod u/->str GithubReleaseSource
  [src]
  (name (source-type src)))

(defmethod ig/init-key ::app-source
  [_ config]
  (log/debug "Initializing app source: GitHub release")
  (map->GithubReleaseSource config))
