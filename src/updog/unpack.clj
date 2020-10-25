(ns updog.unpack
  (:require [taoensso.timbre :as log]
            [updog.util :as u]))

(defmulti unpack-action
  "Unpack downloaded app, dispatched on `action`.
  App's downloaded file/package is at `(:downloaded _app)."
  (fn [_app action] action))

(defmethod unpack-action :default
  [app action]
  (throw (ex-info "Unknown unpack action" {:action action, :app app})))

(defmethod unpack-action :no-unpack
  [{:keys [downloaded]} _]
  (log/debug ::no-unpack {:downloaded downloaded})
  downloaded)

(defmethod unpack-action :unzip
  [{:keys [downloaded tmp-dir]} _]
  (log/debug ::unzip {:downloaded downloaded, :tmp-dir tmp-dir})
  (first (u/unzipped-files downloaded tmp-dir "unpack-")))

(defn unpack-app!
  "Performs all `:unpack` actions on `app` and returns a vector of results."
  [{:keys [unpack] :as app}]
  (some (partial unpack-action app)
        (or (not-empty unpack) [:no-unpack])))
