(ns tailrecursion.boot.core.util
  (:require
   [clojure.java.io    :as io]
   [clojure.stacktrace :as trace]))

(defmacro guard
  "Returns nil instead of throwing exceptions."
  [body & [default]]
  `(try ~@body (catch Throwable _# ~default)))

(defmacro with-rethrow
  "Evaluates body and rethrows any thrown exceptions with the given message."
  [body message]
  `(try ~@body (catch Throwable e# (throw (Exception. ~message e#)))))

(defmacro exit-error
  [& body]
  `(binding [*out* *err*] ~@body (System/exit 1)))

(defmacro exit-ok
  [& body]
  `(try
     ~@body
     (System/exit 0)
     (catch Throwable e#
       (exit-error (trace/print-cause-trace e#)))))

(defn auto-flush
  [writer]
  (proxy [java.io.PrintWriter] [writer]
    (write [s] (.write writer s) (flush))))

(defn warn
  [& more]
  (binding [*out* *err*] (apply printf more) (flush)))

(defn index-of
  [v val]
  (ffirst (filter (comp #{val} second) (map vector (range) v))))

(defn get-resources [name]
  (->> (.. Thread currentThread getContextClassLoader (getResources name))
    enumeration-seq))

(defn copy-resource
  [resource-path out-path]
  (with-open [in  (io/input-stream (io/resource resource-path))
              out (io/output-stream (io/file out-path))]
    (io/copy in out)))

(defn get-project [sym]
  (when-let [pform (->> (get-resources "project.clj") 
                     (map (comp read-string slurp))
                     (filter (comp (partial = sym) second))
                     first)]
    (->> pform (drop 1) (partition 2) (map vec) (into {}))))
