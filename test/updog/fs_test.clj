(ns updog.fs-test
  (:require
    [me.raynes.fs :as rfs]
    [clojure.test :refer [deftest is testing]]
    [updog.fs :as fs])
  (:import
    [java.nio.file.attribute PosixFilePermission]))

(deftest exists?-tests
  (testing "returns false for nil input"
    (is (false? (fs/exists? nil)))))

(deftest file-name-tests
  (testing "returns nil for empty value"
    (doseq [v [nil ""]]
      (testing (pr-str v)
        (is (nil? (fs/file-name v)))))))

(deftest path-tests
  (testing "returns nil for empty value"
    (doseq [v [nil ""]]
      (testing (pr-str v)
        (is (nil? (fs/path v)))))))

(deftest writable-dir?-tests
  (testing "handles invalid input"
    (doseq [v [nil
               "/dev/null" ; not a dir
               "/proc"]] ; shouldn't be writable
      (testing (pr-str v)
        (is (false? (fs/writeable-dir? v)))))))

(deftest expand-home-tests
  (testing "returns input for empty/nil value"
    (doseq [v ["" nil]]
      (testing (pr-str v)
        (is (= v (fs/expand-home v))))))
  (testing "expands ~ in strings"
    (is (= (str (rfs/expand-home "~") "/foo")
           (fs/expand-home "~/foo")))))

(deftest expand-pems-tests
  (testing "0750"
    (is (= (#'fs/expand-perms 0750)
           [PosixFilePermission/OWNER_READ
            PosixFilePermission/OWNER_WRITE
            PosixFilePermission/OWNER_EXECUTE
            PosixFilePermission/GROUP_READ
            PosixFilePermission/GROUP_EXECUTE])))
  (testing "0644"
    (is (= (#'fs/expand-perms 0644)
           [PosixFilePermission/OWNER_READ
            PosixFilePermission/OWNER_WRITE
            PosixFilePermission/GROUP_READ
            PosixFilePermission/OTHERS_READ]))))
