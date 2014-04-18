(ns tailrecursion.boot.core.classloader
  (:require
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core.classlojure :as cl]
   [tailrecursion.boot.core.util        :as util])
  (:import
   [java.net URLClassLoader URL]
   java.lang.management.ManagementFactory))

(defn make-classloader
  [jar-resource-path]
  (let [tmp (doto (io/file ".boot") .mkdirs)
        in  (io/resource jar-resource-path)
        out (io/file tmp jar-resource-path)]
    (with-open [in  (io/input-stream in)
                out (io/output-stream out)]
      (io/copy in out))
    (cl/classlojure (str "file:" (.getPath out)))))

(def dependencies (atom '[[org.clojure/clojure "1.5.1"]]))
(def cl2          (future (make-classloader "boot-classloader.jar")))
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
  [env]
  (let [pod  (make-classloader "boot-podloader.jar")]
    (cl/eval-in pod
      `(do (require 'tailrecursion.boot-podloader)
           (tailrecursion.boot-podloader/add-dirs! ~(into [] (:src-paths env)))
           (tailrecursion.boot-podloader/add-jars! ~(mapv :jar (resolve-deps! env)))))
    (fn [expr] (cl/eval-in pod expr))))
