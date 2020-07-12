(ns updog.apps-db)

(defprotocol AppsDB
  "Interface to a DB containing application data and version information."

  (initialize! [_]
    "Initializes the application database.")

  (assoc-app! [_ app-key app]
    "Adds the `app` under the given key.")

  (assoc-field! [_ app-key field value]
    "Set `value` for `field` of application with key `app-key`.")

  (dissoc-app! [_ app-key]
    "Remove `app-key` from the database.")

  (get-app [_ app-key]
    "Lookup the application data for `app-key`, or nil if not found.")

  (get-all-apps [_]
    "List all app keys."))
