(ns updog.main
  (:require
    [clojure.string :as str]
    [taoensso.encore :as enc]
    [taoensso.timbre :as log]
    [updog.app :as app]
    [updog.config :as config])
  (:gen-class)
  (:import
    [clojure.lang ExceptionInfo]))

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

(def ^:private default-log-level :info)

(defn init-logging!
  "See `(clojure.repl/doc log/*config*)` for documentation on log levels."
  [log-level]
  (log/swap-config! #(assoc % :output-fn simple-output-fn))
  (log/set-level! (or log-level default-log-level))
  (log/debug "Logging initialized with log leve" log-level))

(defn- log-app-update-result
  [result app-config]
  (let [{:keys [app-key]} app-config
        {:keys [status]} result]
    (cond
      (= status ::app/app-updated)
      (do
        (log/info "✅" app-key "updated to version" (:installed-version result))
        (log/info "📄 Installed files:" (str/join ", " (:installed-files result))))

      (= status ::app/already-up-to-date)
      (log/info "🟰" app-key "already up-to-date:" (:installed-version result))

      (= status ::app/unexpected-error)
      (log/error "An unexpected error has occurred:" (:error result))

      :else
      (log/warn "Unexpected update status:" status))))

(defn update-apps!
  [config]
  (doall
    (for [app-config (vals config)]
      (let [result (app/process app-config)]
        (log-app-update-result result app-config)
        result))))

(defn- prepped-app-configs
  [{:updog/keys [defaults] :as config}]
  (into {}
        (for [[app-key app-config] config
              :when (not= (namespace app-key) "updog")]
          [app-key (-> (merge defaults app-config)
                       (config/app-prep app-key))])))

(defn -main
  [config-fname]
  (let [config (config/read config-fname)
        updog-config (:updog/config config)
        config (dissoc config :updog/config)]
    (init-logging! (get updog-config :log-level default-log-level))
    (when-let [log-fname (:update-log-file updog-config)]
      (reset! app/custom-update-log log-fname)
      (log/debug "Using custom update log:" log-fname))
    (try
      (log/debug "Loaded config:" (pr-str config))
      (let [pconfig (prepped-app-configs config)]
        (log/debug "Prepared config:" (pr-str pconfig))
        (update-apps! pconfig))
      (catch ExceptionInfo ex
        (log/error "Error updating apps:" ex)
        (System/exit 1)))))

(comment
  (def config-fname "test-config.edn")
  (def config (prepped-app-configs (config/read config-fname)))
  (def app-key :clj-kondo/clj-kondo)
  (def app-key :clojure-lsp/clojure-lsp)
  (def app-key :babashka/babashka)
  (def app-config (get config app-key))

  (update-apps! {:updog/defaults
                 {:install-dir "~/.local/bin/"
                  :archive-dir "~/tmp"}
                 :clj-kondo/clj-kondo
                 {:asset "linux-static-amd64"}})
  (Throwable->map *e)
  )
