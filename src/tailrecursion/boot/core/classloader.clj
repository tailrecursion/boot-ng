(ns tailrecursion.boot.core.classloader
  (:require
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core.classlojure :as cl]
   [tailrecursion.boot.core.util        :as util])
  (:import
   [java.net URLClassLoader URL URI]
   java.lang.management.ManagementFactory))

(defn copy-resource
  [resource-path out-path]
  (with-open [in  (io/input-stream (io/resource resource-path))
              out (io/output-stream (io/file out-path))]
    (io/copy in out)))

(defn make-classloader
  []
  (let [tmp  (doto (io/file ".boot") .mkdirs)
        path (-> "boot-classloader-resource-path" io/resource slurp .trim)
        jar  (io/file tmp path)]
    (when (.createNewFile jar) (copy-resource path jar))
    (cl/classlojure (str "file:" (.getPath jar)))))

(defn make-podloader
  [jar-file-path]
  (cl/classlojure (str "file:" (.getPath (io/file jar-file-path)))))

(def cl2          (future (make-classloader)))
(def dependencies (atom '[[org.clojure/clojure "1.5.1"]]))
(def dfl-env      {:repositories #{"http://clojars.org/repo/" "http://repo1.maven.org/maven2/"}})

;;;;

(defn index-of
  [v val]
  (ffirst (filter (comp #{val} second) (map vector (range) v))))

(defn exclude
  [syms coordinate]
  (if-let [idx (index-of coordinate :exclusions)]
    (let [exclusions (get coordinate (inc idx))]
      (assoc coordinate (inc idx) (into exclusions syms)))
    (into coordinate [:exclusions syms])))

(defn add-dirs!
  [dirs]
  (when (seq dirs)
    (let [meth  (doto (.getDeclaredMethod URLClassLoader "addURL" (into-array Class [URL]))
                  (.setAccessible true))
          cldr  (ClassLoader/getSystemClassLoader)
          dirs  (->> dirs (map io/file) (filter #(.exists %)) (map #(.. % toURI toURL)))]
      (doseq [url dirs] (.invoke meth cldr (object-array [url]))))))

(defn resolve-deps!
  [env]
  (cl/eval-in @cl2
    `(do (require 'tailrecursion.boot-classloader)
         (tailrecursion.boot-classloader/resolve-dependencies! '~(merge dfl-env env)))))

(defn add-deps!
  [env]
  (let [{deps :dependencies :as env} (merge dfl-env env)
        loaded (->> @dependencies (map first) set)
        specs  (->> deps
                 (remove (comp (partial contains? loaded) first))
                 (mapv (partial exclude (vec loaded)))
                 (assoc env :dependencies)
                 resolve-deps!)]
    (swap! dependencies into (mapv :dep specs))
    (add-dirs! (map #(URL. (str "file://" (:jar %))) specs))))

(defn glob-match?
  [pattern path]
  (cl/eval-in @cl2
    `(do (require 'tailrecursion.boot-classloader)
         (tailrecursion.boot-classloader/glob-match? ~pattern ~path))))

(defn make-pod
  [& {:keys [src-paths] :as env}]
  (let [env   (merge dfl-env env)
        clj?  #(= 'org.clojure/clojure (first (:dep %)))
        {[{clj-jar :jar}] true, other-deps false}
        (->> env resolve-deps! (group-by clj?))]
    (when-not (and clj-jar (.exists (io/file clj-jar)))
      (throw (Exception. "Pod has no Clojure dependency")))
    (let [pod   (make-podloader clj-jar)
          deps  (->> other-deps (mapv :jar) (into src-paths))
          files (->> deps (mapv io/file) (filter #(.exists %)))]
      (doseq [file files] (.addURL pod (-> file (.. toURI toURL))))
      (fn [expr] (cl/eval-in pod expr)))))
