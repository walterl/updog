(ns updog.archive
  "File archive handling"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [me.raynes.fs.compression :as fs.comp]
    [updog.fs :as fs])
  (:import
    [org.apache.commons.compress.archivers.tar TarArchiveInputStream TarFile]
    [org.apache.commons.compress.archivers.zip ZipFile]
    [org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream]
    [org.apache.commons.compress.compressors.gzip GzipCompressorInputStream]))

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

(defn- extract-archive
  "Extract `files` from `archive` to `dest-dir`.
  `archive` must be a `TarFile` or `ZipFile`."
  [archive files dest-dir]
  (let [files (set (or files []))
        entries (if (instance? ZipFile archive)
                  (enumeration-seq (.getEntries archive))
                  (.getEntries archive))
        entries (filter #(or (empty? files) (contains? files (.getName %))) entries)]
    (doall
      (for [entry entries
            :let [base-name (fs/file-name (.getName entry))
                  dest (fs/path dest-dir base-name)]]
        (do
          (with-open [istream (.getInputStream archive entry)
                      ostream (io/output-stream dest)]
            (io/copy istream ostream))
          dest)))))

(defmethod extract ".zip"
  [filename archive-files dest-dir]
  (with-open [zf (ZipFile. filename)]
    (extract-archive zf archive-files dest-dir)))

(defmethod extract ".bz2"
  [filename archive-files dest-dir]
  (let [bunzipped (fs/path (fs/temp-dir) (str/replace (fs/file-name filename) #"\.bz2$" ""))]
    (fs.comp/bunzip2 filename bunzipped)
    (try
      (extract bunzipped archive-files dest-dir)
      (finally
        (fs/delete bunzipped)))))

(defmethod extract ".gz"
  [filename archive-files dest-dir]
  (let [gunzipped (fs/path (fs/temp-dir) (str/replace (fs/file-name filename) #"\.gz$" ""))]
    (fs.comp/gunzip filename gunzipped)
    (try
      (extract gunzipped archive-files dest-dir)
      (finally
        (fs/delete gunzipped)))))

(defmethod extract ".tar"
  [filename archive-files dest-dir]
  (with-open [tf (TarFile. (io/file filename))]
    (extract-archive tf archive-files dest-dir)))

(comment
  (import '[org.apache.commons.compress.compressors.gzip GzipCompressorInputStream])
  (import '[org.apache.commons.compress.archivers.tar TarArchiveInputStream TarFile])

  (def filename "/tmp/babashka-0.6.8-linux-amd64-static.tar.gz")
  (def filename "/tmp/thing.tar.bz2")

  (def gzis (GzipCompressorInputStream. (io/input-stream filename)))
  (def taris (TarArchiveInputStream. (GzipCompressorInputStream. (io/input-stream filename))))
  (def entries
    (loop [entries []]
      (if-let [entry (.getNextEntry taris)]
        (recur (conj entries entry))
        entries)))
  (def entry (first entries))

  (extract filename ["bb"] (fs/expand-home "~/tmp/updog-test/"))
  (extract filename ["LICENSE"] (fs/expand-home "~/tmp/updog-test/"))
  *e
  ,)

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

(defn- executable?
  [mode]
  (some #(pos? (bit-and % fs/PERM_X))
        (vals (fs/unpack-perms mode))))

(defmethod list-executables ".zip"
  [filename]
  (with-open [zf (ZipFile. filename)]
    (for [zip-entry (enumeration-seq (.getEntries zf))
          :when (and (not (.isDirectory zip-entry))
                     (executable? (.getUnixMode zip-entry)))]
      (.getName zip-entry))))

(defn- tar-executables
  [^TarArchiveInputStream istream]
  (loop [names []]
    (let [entry (.getNextEntry istream)]
      (if (some? entry)
        (recur
          (if (and (not (.isDirectory entry))
                   (executable? (.getMode entry)))
            (conj names (.getName entry))
            names))
        names))))

(defmethod list-executables ".bz2"
  [filename]
  (with-open [is (io/input-stream filename)
              bzis (BZip2CompressorInputStream. is)
              tis (TarArchiveInputStream. bzis)]
    (tar-executables tis)))

(defmethod list-executables ".gz"
  [filename]
  (with-open [is (io/input-stream filename)
              gzis (GzipCompressorInputStream. is)
              tis (TarArchiveInputStream. gzis)]
    (tar-executables tis)))

(defmulti largest-file
  "Returns the name of the largest file in the specified archive."
  (fn [filename]
    (fs/extension filename)))

(defmethod largest-file :default
  [filename]
  (throw (unsupported-archive filename)))

(defmethod largest-file ".zip"
  [filename]
  (with-open [zf (ZipFile. filename)]
    (:name (reduce
             (fn [largest curr]
               (if (< (get largest :size 0) (.getSize curr))
                 {:name (.getName curr)
                  :size (.getSize curr)}
                 largest))
             {}
             (enumeration-seq (.getEntries zf))))))

(defn- largest-tar-entry
  [^TarArchiveInputStream istream]
  (loop [largest nil
         size -1]
    (let [entry (.getNextEntry istream)
          curr-size (when entry (.getSize entry))]
      (if (some? entry)
        (if (< size curr-size)
          (recur (.getName entry) curr-size)
          (recur largest size))
        largest))))

(defmethod largest-file ".bz2"
  [filename]
  (with-open [is (io/input-stream filename)
              bzis (BZip2CompressorInputStream. is)
              tis (TarArchiveInputStream. bzis)]
    (largest-tar-entry tis)))

(defmethod largest-file ".gz"
  [filename]
  (with-open [is (io/input-stream filename)
              bzis (GzipCompressorInputStream. is)
              tis (TarArchiveInputStream. bzis)]
    (largest-tar-entry tis)))
