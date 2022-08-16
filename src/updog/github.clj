(ns updog.github
  (:require
    [clojure.spec.alpha :as s]
    [cheshire.core :as json]))

(def ^:private github-releases-url
  "https://api.github.com/repos/%s/releases")

(def ^:private github-latest-release-url
  (str github-releases-url "/latest"))

(defn fetch-latest-version
  [repo]
  (try
   (-> (format github-latest-release-url repo)
       slurp
       (json/parse-string true))
   (catch java.io.FileNotFoundException _
     ;; If there is no latest release, let's use the first release, which is probably a pre-release
     (-> (format github-releases-url repo)
         slurp
         (json/parse-string true)
         first))))

(defn- valid-repo-slug?
  [s]
  (re-find #"^[\w\-]+/[\w\-]+$" s))

(s/def ::repo-slug (s/nilable (and string? valid-repo-slug?))) ; TODO Add repo-slug generator
