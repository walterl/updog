(ns updog.archive
  "File archive handling"
  (:require
    [clojure.java.io :as io]
    [updog.fs :as fs]) 
  (:import
    [org.apache.commons.compress.archivers.zip ZipFile]))

(defmulti extract
  "Extract `_archive-files` from archive `_filename` into `_dest-dir`.

  Archive type is determined from `filename`'s extension."
  (fn [filename _archive-files _dest-dir]
    (fs/extension filename)))

(defmethod extract :default
  [filename archive-files dest-dir]
  (throw (ex-info "Unsupported archive"
                  {:type ::unsupported-archive
                   :archive filename
                   :archive-files archive-files
                   :dest-dir dest-dir})))

(defmethod extract ".zip"
  [filename archive-files dest-dir]
  (let [zf (ZipFile. filename)
        archive-files (set archive-files)
        entries (filter #(contains? archive-files (.getName %))
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
