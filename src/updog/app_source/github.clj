(ns updog.app-source.github
  (:require [cheshire.core :as json]
            [integrant.core :as ig]
            [taoensso.timbre :as log]
            [updog.app-source :refer [AppSource]]))

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

(defrecord GithubAppSource []
  AppSource
  (source-type
    [_]
    :github)

  (fetch-latest-version!
    [_ app-data]
    (log/debug ::fetch-latest-version! app-data)
    (let [url            (format github-latest-release-url (:repo app-data))
          latest-version (-> (slurp url)
                             (json/parse-string true))
          asset          (some (partial asset-matcher (:asset-selector app-data))
                               (:assets latest-version))]
      {:version      (tag->version (:version-regex app-data)
                                   (:tag_name latest-version))
       :filename     (:name asset)
       :download-url (:browser_download_url asset)})))

(defmethod ig/init-key ::app-source
  [_ config]
  (map->GithubAppSource config))
