(ns tailrecursion.boot
  (:require
   [clojure.java.io                :as io]
   [classlojure.core               :as cl]
   [clojure.stacktrace             :as trace]
   [clojure.pprint                 :as pprint]
   [tailrecursion.boot-classloader :as loader])
  (:import
   [java.net URL URLClassLoader]
   [clojure.lang DynamicClassLoader])
  (:gen-class))

(defn url-str [deps]
  (->> deps first :jar io/file .getPath (str "file:")))

(defn clj-dep []
  (loader/resolve-dependencies!
    {:repositories #{"http://repo1.maven.org/maven2/" "http://clojars.org/repo/"}
     :dependencies '[[org.clojure/clojure "1.5.1"]]}))

(defn core-dep []
  (loader/resolve-dependencies!
    {:repositories #{"http://clojars.org/repo/"}
     :dependencies '[[tailrecursion/boot-core "2.0.0-SNAPSHOT"]]}))

(defn- dyn-classloader [urls ext]
  (let [loader (DynamicClassLoader. ext)
        urls   (map io/as-url (flatten urls))]
    (doseq [u urls] (.addURL loader u))
    loader))

(defn make-cl [& urls]
  (let [^URLClassLoader cl (dyn-classloader urls cl/ext-classloader)]
    (.loadClass cl "clojure.lang.RT")
    (cl/eval-in* cl '(require 'clojure.main))
    cl))

(defn get-resources [name]
  (->> (.. Thread currentThread getContextClassLoader (getResources name))
    enumeration-seq))

(defn get-project [sym]
  (when-let [pform (->> (get-resources "project.clj") 
                     (map (comp read-string slurp))
                     (filter (comp (partial = sym) second))
                     first)]
    (->> pform (drop 1) (partition 2) (map vec) (into {}))))

(defn boot-version []
  (-> 'tailrecursion/boot get-project (get 'tailrecursion/boot)))

(defn update []
  (->> (-> (core-dep) url-str (subs 5) io/file .getParentFile file-seq)
    (filter #(.isFile %))
    (mapv #(.delete %))))

(defn -main [& args]
  (try
    (if (= args '("--update"))
      (update)
      (let [clj-url  (url-str (clj-dep))
            core-url (url-str (core-dep))
            core-pod (make-cl clj-url core-url)]
        (cl/eval-in core-pod
          `(do (require 'tailrecursion.boot)
               (tailrecursion.boot/-main ~(boot-version) ~@args)))))
    (System/exit 0)
    (catch Throwable e
      (trace/print-cause-trace e)
      (System/exit 1))))
