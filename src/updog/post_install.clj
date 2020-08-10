(ns updog.post-install
  (:require [taoensso.timbre :as log]
            [updog.util :as u]))

(defmulti post-install-action
  "TODO"
  (fn [_app action] action))

(defmethod post-install-action :default
  [_app action]
  (log/errorf "Unknown post-install action:" action))

(defmethod post-install-action :chmod+x
  [{:keys [installed]} _]
  (u/chmod+x installed)
  installed)

(defmethod post-install-action :shell-script
  [{:keys [shell-script] :as app} _]
  (u/shell-script shell-script (select-keys app [:downloaded :install-file :installed])))

(defn post-install-app!
  "Applies all `:post-install` actions to `app` and returns a vector of
  results."
  [{:keys [post-install] :as app}]
  (mapv (partial post-install-action app) post-install))
