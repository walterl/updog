(ns updog.archive
  "File archive handling"
  (:require
    [clojure.java.io :as io]
    [updog.fs :as fs])
  (:import
    [org.apache.commons.compress.archivers.zip ZipFile]))

(defn- unsupported-archive
  [filename]
  (ex-info "Unsupported archive" {:type ::unsupported-archive, :archive filename}))

(defmulti extract
  "Extract `_archive-files` from archive `_filename` into `_dest-dir`.

  Archive type is determined from `filename`'s extension."
  (fn [filename _archive-files _dest-dir]
    (fs/extension filename)))

(defmethod extract :default
  [filename _archive-files _dest-dir]
  (throw (unsupported-archive filename)))

(defmethod extract ".zip"
  [filename archive-files dest-dir]
  (let [zf (ZipFile. filename)
        archive-files (set (or archive-files []))
        entries (filter #(or (empty? archive-files) (contains? archive-files (.getName %)))
                        (enumeration-seq (.getEntries zf)))]
    (doall
      (for [entry entries
            :let [base-name (fs/file-name (.getName entry))
                  dest (fs/path dest-dir base-name)]]
        (do
          (with-open [istream (.getInputStream zf entry)
                      ostream (io/output-stream dest)]
            (io/copy istream ostream))
          dest)))))

(defmethod extract ".tar.bz2"
  [filename archive-files dest-dir]
  ;; TODO
  )

(defmethod extract ".tar.gz"
  [filename archive-files dest-dir]
  ;; TODO
  )

(comment
  (extract "/files/Dev/Clojure/clj-kondo.zip" ["clj-kondo"] "/tmp/xxx")

  (let [zf (ZipFile. "/files/Dev/Clojure/babashka.tar.gz")]
    (mapv #(.getName %) (enumeration-seq (.getEntries zf)))
    #_(map #(.getName %) (java.util.Collection/list (.getEntries zf))))
  (Throwable->map *e)
  ,)

(defmulti list-executables
  "Returns a list of all executable files' names in the specified archive."
  (fn [filename]
    (fs/extension filename)))

(defmethod list-executables :default
  [filename]
  (throw (unsupported-archive filename)))

(defmethod list-executables ".zip"
  [filename]
  (for [zip-entry (enumeration-seq (.getEntries (ZipFile. filename)))
        :let [perms (fs/unpack-perms (.getUnixMode zip-entry))]
        :when (some #(pos? (bit-and % fs/PERM_X)) (vals perms))]
    (.getName zip-entry)))

(defmethod list-executables ".tar.bz2"
  [filename]
  ;; TODO
  )

(defmethod list-executables ".tar.gz"
  [filename]
  ;; TODO
  )

(defmulti largest-file
  "Returns the name of the largest file in the specified archive."
  (fn [filename]
    (fs/extension filename)))

(defmethod largest-file :default
  [filename]
  (throw (unsupported-archive filename)))

(defmethod largest-file ".zip"
  [filename]
  (:name (reduce
           (fn [largest curr]
             (if (< (get largest :size 0) (.getSize curr))
               {:name (.getName curr)
                :size (.getSize curr)}
               largest))
           {}
           (enumeration-seq (.getEntries (ZipFile. filename))))))

(defmethod largest-file ".tar.bz2"
  [filename]
  ;; TODO
  )

(defmethod largest-file ".tar.gz"
  [filename]
  ;; TODO
  )
