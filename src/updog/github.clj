(ns updog.github
  (:require
    [camel-snake-kebab.core :as csk]
    [camel-snake-kebab.extras :as cske]
    [clojure.spec.alpha :as s]
    [updog.net :as net]))

(def ^:private github-releases-url
  "https://api.github.com/repos/%s/releases")

(defn fetch-release-version
  [repo release]
  (let [repo-release-url (format github-releases-url repo)]
    (cske/transform-keys
      csk/->kebab-case-keyword
      (if (= :latest release)
        (net/fetch-json (str repo-release-url "/latest"))
        (->> (net/fetch-json repo-release-url)
             (filter #(= (:tag_name %) release))
             (first))))))

(comment
  (fetch-release-version "clj-kondo/clj-kondo" :latest)
  (fetch-release-version "clj-kondo/clj-kondo" "v2022.06.22")
  ,)

(comment
  (def repo "walterl/updog")
  (def release :latest)
  )

(defn fetch-release-assets
  ([repo]
   (fetch-release-assets repo :latest))
  ([repo release]
   (let [{:keys [assets tag-name]} (fetch-release-version repo release)]
     (for [asset assets]
       (assoc (select-keys asset [:name :label :size :content-type :created-at :updated-at])
              :download-url (:browser-download-url asset)
              :tag-name tag-name)))))

(comment
  (fetch-release-assets "clj-kondo/clj-kondo")
  (fetch-release-assets "clj-kondo/clj-kondo" "v2022.06.22")
  ,)

(defn- valid-repo-slug?
  [s]
  (re-find #"^[\w\-]+/[\w\-]+$" s))

(s/def ::repo-slug (s/nilable (and string? valid-repo-slug?))) ; TODO Add repo-slug generator
