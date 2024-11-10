(ns updog.main
  (:require
    [clojure.pprint :refer [pprint]]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]
    [updog.app :as app]
    [updog.config :as config])
  (:gen-class))

(def ^:private level-marker
  {:trace  "[🔍 TRACE]"
   :debug  "[🪲 DEBUG]"
   :info   "[ℹ️  INFO]"
   :warn   "[⚠️  WARN]"
   :error  "[🛑 ERROR]"
   :fatal  "[☣️  FATAL]"
   :report "[🛑🛑🛑 REPORT]"})

(defn- simple-output-fn
  "Adapted from taoensso.timbre/default-output-fn for simpler output format."
  ([     data] (simple-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace?]} opts
         {:keys [level ?err msg_]} data]
     (str
       (get level-marker level "x")
       " "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str enc/system-newline (log/stacktrace err opts))))))))

(defn init-logging!
  "See `(clojure.repl/doc log/*config*)` for documentation on log levels."
  ([] (init-logging! :info))
  ([log-level]
   (log/swap-config! #(assoc % :output-fn simple-output-fn))
   (when log-level
     (log/set-level! log-level))))

(defn- log-update-result
  [{::keys [config result]}]
  (let [{:keys [app-key]} config
        {:keys [status]} result]
    (cond
      (= status ::app/app-updated)
      (log/info app-key "updated ✅")

      (= status ::app/already-up-to-date)
      (log/info app-key "already up-to-date 🟰")

      (= status ::app/unexpected-error)
      (log/error "An unexpected error has occurred:" (:error result))

      :else
      (log/warn "Unexpected update status ❓:" status))))

(defn update-apps!
  [{:updog/keys [defaults] :as config}]
  (doall
    (for [[app-key app-config] config
          :when (not= :updog/defaults app-key)
          :let [config (-> (merge defaults app-config)
                           (config/app-prep app-key))
                result {::config config
                        ::result (app/process config)}]]
      (do
        (log-update-result result)
        result))))

(defn -main
  [config-fname]
  (init-logging!)
  (update-apps! (config/read config-fname)))

(comment
  (def config-fname "test-config.edn")
  (def config (config/read config-fname))
  (def defaults (:updog/defaults config))
  (def app-key :clj-kondo/clj-kondo)
  (def app-key :clojure-lsp/clojure-lsp)
  (def app-config (get config app-key))
  (def prepped-config (-> (merge defaults app-config)
                          (config/app-prep app-key)))

  (update-apps! {:updog/defaults
                 {:install-dir "~/.local/bin/"
                  :archive-dir "~/tmp"}
                 :clj-kondo/clj-kondo
                 {:asset "linux-static-amd64"}})
  (Throwable->map *e)
  )
