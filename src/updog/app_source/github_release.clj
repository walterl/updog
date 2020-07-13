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
  (source-type
    [_]
    :github-release)

  (fetch-latest-version!
    [_ app]
    (log/debug ::fetch-latest-version! app)
    (let [url            (format github-latest-release-url (:repo app))
          latest-version (-> (slurp url)
                             (json/parse-string true))
          asset          (some (partial asset-matcher (:asset-selector app))
                               (:assets latest-version))]
      {:version      (tag->version (:version-regex app)
                                   (:tag_name latest-version))
       :filename     (:name asset)
       :download-url (:browser_download_url asset)})))

(defmethod u/->str GithubReleaseSource
  [src]
  (name (source-type src)))

(defmethod ig/init-key ::app-source
  [_ config]
  (map->GithubReleaseSource config))
