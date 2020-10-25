(ns updog.cli
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [me.raynes.fs :as fs]
            [taoensso.timbre :as log]
            [updog.apps-db :as apps-db]
            [updog.updater :as updater]
            [updog.system :as sys])
  (:import clojure.lang.ExceptionInfo))

(def ^:private cli-options
  [["-d" "--db APPSDB" "EDN apps database to use."
    :default  "appsdb.edn"
    :validate [fs/exists? "Apps database file not found."]]
   ["-v" "--verbose" "Enable verbose output (set log level to debug)."]
   ["-h" "--help" "Display help text and exit."]] )

(defn- read-edn
  [stream-or-filename]
  (edn/read-string (slurp stream-or-filename)))

(def ^:private commands
  (sorted-map
   :update {:usage   (str/join
                      \newline
                      ["updog update [<options>] [app-key [app-key ...]]"
                       ""
                       "Update all/specified apps."])
            :options [["-h" "--help" "Display help for run command and exit."]]}
   :list   {:usage   "updog list [<options>]\n\nList keys in app db."
            :options [["-h" "--help" "Display help for list command and exit."]]}
   :get    {:usage   (str "updog get [<options>] [<app-key> [<app-key>...]]\n\n"
                          "Output details of all/specified apps.")
            :options [["-h" "--help" "Display help for get command and exit."]]}
   :set    {:usage   (str "updog set <app-key> <field> <value>\n\n"
                          "Update app's field value.")
            :options [["-e" "--edn" "Parse value as EDN."]
                      ["-h" "--help" "Display help for set command and exit."]]}
   :add    {:usage   (str/join
                      \newline
                      ["updog add [<options>] <app-key>"
                       ""
                       (str "Add app with key to app db. "
                            "App details are read from stdin.")
                       ""
                       "Example:"
                       "$ updog add clj-kondo"
                       "{:name         \"clj-kondo linter\""
                       " :source-type  :github-release"
                       " :github-repo  \"borkdude/clj-kondo\""
                       " :dest-path    \"$HOME/.local/bin/clj-kondo\""
                       " :unpack       [:unzip]"
                       " :post-install [:chmod+x]}"])
            :options [["-i" "--input FILENAME" "Read app EDN from FILENAME."
                       :default      *in*
                       :default-desc "STDIN"
                       :validate     [fs/exists? "File not found"]]
                      ["-h" "--help" "Display help for add command and exit."]]}
   :rm     {:usage   (str "updog rm <app-key>\n\n"
                          "Delete app with specified key from app db.")
            :options [["-h" "--help" "Display help for rm command and exit."]]}))

(defn- args->app-key
  [args]
  (-> args first keyword))

(defn- error-on-unexpected-args!
  [{:keys [options], :as err-data}]
  (when (seq (:arguments options))
    (throw (ex-info "Unexpected arguments"
                    (assoc err-data :type ::unexpected-arguments)))))

(defn- error-on-required-arg!
  [{:keys [options], :as err-data} arg-name]
  (when-not (seq (:arguments options))
    (throw (ex-info (str "Missing request argument " arg-name)
                    (assoc err-data
                           :type     ::missing-arg
                           :arg-name arg-name)))))

(defn- error-on-invalid-app-key!
  [{:keys [options system], :as err-data}]
  (let [app-key      (args->app-key (:arguments options))
        {:keys [db]} (::updater/single-run-updater system)]
    (when-not (seq (apps-db/get-app db app-key))
      (throw (ex-info "No app with key" (assoc err-data
                                               :type    ::invalid-app-key
                                               :app-key app-key))))))

(defmulti command!
  "Dispatched on `cmd`."
  (fn [_sys cmd _options] cmd))

(defmethod command! :default
  [sys cmd options]
  (throw (ex-info "Unknown command" {:type    ::unknown-command
                                     :system  sys
                                     :cmd     cmd
                                     :options options})))

(defmethod command! :list
  [sys cmd options]
  (error-on-unexpected-args! {:system sys, :cmd cmd, :options options})
  (let [{:keys [db]} (::updater/single-run-updater sys)]
    (log/debug ::list-all-apps (keys (apps-db/get-all-apps db)))
    (str/join \newline (for [app-key (keys (apps-db/get-all-apps db))]
                         (name app-key)))))

(defmethod command! :get
  [sys _ {:keys [arguments]}]
  (let [{:keys [db]} (::updater/single-run-updater sys)]
    (cond-> (apps-db/get-all-apps db)
      (seq arguments) (select-keys (map keyword arguments)))))

(defmethod command! :set
  [sys cmd {:keys [arguments], :as options}]
  (let [{:keys [db]}     (::updater/single-run-updater sys)
        app-key          (args->app-key arguments)
        [field & values] (rest arguments)
        field            (keyword field)
        err-data         {:system sys, :cmd cmd, :options options}
        value            (cond-> (str/join " " values)
                           (get-in options [:options :edn]) edn/read-string)]
    (error-on-invalid-app-key! err-data)
    (when-not field
      (throw (ex-info "No field specified"
                      (assoc err-data :type ::missing-field))))
    (when-not (not-empty value)
      (throw (ex-info "No value specified"
                      (assoc err-data :type ::missing-value))))
    (get-in (apps-db/assoc-field! db app-key field value)
            [:apps app-key])))

(defmethod command! :update
  [sys _ {:keys [arguments]}]
  (updater/start-update! (::updater/single-run-updater sys)
                         arguments))

(defmethod command! :add
  [sys cmd {:keys [arguments], {:keys [input]} :options, :as options}]
  (let [app-key      (args->app-key arguments)
        {:keys [db]} (::updater/single-run-updater sys)]
    (when-not app-key
      (throw (ex-info "No app key specified"
                      {:type    ::missing-app-key
                       :system  sys
                       :cmd     cmd
                       :options options})))
    (get-in (apps-db/assoc-app! db app-key (read-edn input))
            [:apps app-key])))

(defmethod command! :rm
  [sys cmd {:keys [arguments], :as options}]
  (let [app-key      (args->app-key arguments)
        {:keys [db]} (::updater/single-run-updater sys)
        err-data     {:system sys, :cmd cmd, :options options}]
    (error-on-required-arg! err-data "app key")
    (error-on-invalid-app-key! err-data)
    (get-in (apps-db/dissoc-app! db app-key)
            [:apps app-key])))

(defn- cmd-options
  [cmd]
  (let [cmd-kw (keyword cmd)]
    (when-let [opts (not-empty (get-in commands [cmd-kw :options]))]
      [cmd-kw opts])))

(defn- parse-args
  [args]
  (let [parsed           (cli/parse-opts args cli-options :in-order true)
        [cmd & cmd-args] (:arguments parsed)
        [cmd cmd-opts]   (cmd-options cmd)]
    (cond-> [parsed]
      cmd (into [cmd (cli/parse-opts cmd-args cmd-opts)]))))

(defn- appended-commands
  [summary]
  (let [cmds (str/join " " (map name (keys commands)))]
    (str/join \newline [summary "" (str "Commands: " cmds)])))

(defn- summary
  [cmd]
  (let [options (if cmd
                  (get-in commands [cmd :options])
                  cli-options)]
    (cond-> (:summary (cli/parse-opts [] options))
      (not cmd) appended-commands)))

(defn- cmd-usage
  [cmd]
  (if cmd
    (get-in commands [cmd :usage])
    "updog <options> [<command> [<command-options>]]"))

(defn- usage
  ([] (usage nil))
  ([cmd]
   (str/join \newline
             ["updog - automating manual software updates"
              ""
              (format "Usage: %s" (cmd-usage cmd))
              ""
              "Options:"
              (summary cmd)])))

(defn- error-type
  [ex]
  (:type (ex-data ex)))

(defn- error-message
  [ex]
  (let [{:keys [cmd], ex-type :type} (ex-data ex)]
    (or (condp = ex-type
          ::unexpected-arguments
          (format "Command %s doesn't accept arguments." cmd)
          ;; else
          nil)
        (ex-message ex))))

(defn- run-command!
  [db-file cmd opts]
  (log/debug ::command! cmd opts)
  (let [system (atom nil)]
    (try
      (reset! system (sys/init db-file))
      (let [cmd-output (command! @system cmd opts)]
        {:output (or cmd-output "Done.")})
      (catch ExceptionInfo ex
        {:error {:type (error-type ex)
                 :msg  (error-message ex)}})
      (finally
        (sys/halt! @system)))))

(defn- format-errors
  [errors]
  {:error {:type ::options-errors
           :msg  (str/join \newline errors)}})

(defn main
  "Entrypoint for CLI. Call with command-line args."
  [args]
  (let [[{:keys [errors options]} cmd cmd-opts]
        (parse-args args)

        errors (into errors (:errors cmd-opts))]
    (log/set-level! (if (:verbose options) :debug :info))
    (log/debug ::cli-main args options cmd cmd-opts errors)
    (cond
      (not-empty errors)
      (format-errors errors)

      (or (:help options)
          (not cmd))
      {:output (usage)}

      (get-in cmd-opts [:options :help])
      {:output (usage cmd)}

      :else
      (run-command! (:db options) cmd cmd-opts))))
