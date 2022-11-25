(ns updog.github
  (:require
    [clojure.spec.alpha :as s]
    [updog.net :as net]))

(def ^:private github-releases-url
  "https://api.github.com/repos/%s/releases")

(defn fetch-release-version
  [repo release]
  (let [repo-release-url (format github-releases-url repo)]
    (if (= :latest release)
      (net/fetch-json (str repo-release-url "/latest"))
      (->> (net/fetch-json repo-release-url)
           (filter #(= (:tag_name %) release))
           first))))

(comment
  (fetch-release-version "clj-kondo/clj-kondo" :latest)
  (fetch-release-version "clj-kondo/clj-kondo" "v2022.06.22")
  ,)

(defn fetch-release-asset-urls
  ([repo]
   (fetch-release-asset-urls repo :latest))
  ([repo release]
   (let [{:keys [assets]} (fetch-release-version repo release)]
     (for [{:keys [label], download-url :browser_download_url} assets]
       {:label label, :download-url download-url}))))

(comment
  (fetch-release-asset-urls "clj-kondo/clj-kondo")
  (fetch-release-asset-urls "clj-kondo/clj-kondo" "v2022.06.22")
  ,)

(defn- valid-repo-slug?
  [s]
  (re-find #"^[\w\-]+/[\w\-]+$" s))

(s/def ::repo-slug (s/nilable (and string? valid-repo-slug?))) ; TODO Add repo-slug generator
