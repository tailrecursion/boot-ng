(ns tailrecursion.boot.core.env
  (:require [clojure.java.io :as io]))

(def +env+
  "Environment variables used by boot to configure itself."
  {:HOME         (System/getenv "HOME")
   :BOOT_DIR     (System/getenv "BOOT_DIR")
   :BOOT_SCRIPT  (System/getenv "BOOT_SCRIPT")})

(def +boot-dir+
  "Directory where boot keeps classloader jar files, rc config scripts, etc."
  (let [envdir (:BOOT_DIR +env+)
        dfldir (io/file (:HOME +env+) ".boot")]
    (doto (io/file (or envdir dfldir)) .mkdirs)))
