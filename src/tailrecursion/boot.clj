(ns tailrecursion.boot
  (:require
   [clojure.java.io           :as io]
   [clojure.tools.cli         :as cli]
   [clojure.stacktrace        :as trace]
   [clojure.pprint            :as pprint]
   [tailrecursion.maven       :as maven]
   [tailrecursion.classloader :as loader])
  (:import
   [org.springframework.util AntPathMatcher])
  (:gen-class))

(defmacro guard [expr]
  `(try ~expr (catch Throwable e#)))

(defn glob-match?
  [pattern path]
  (.match (AntPathMatcher.) pattern path))

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

(defn parse-opts [args argspecs & options]
  (apply cli/parse-opts args argspecs options))

(defn parse-boot-opts [args]
  (require 'tailrecursion.boot.core.cli-opts)
  (when-let [opts (resolve 'tailrecursion.boot.core.cli-opts/opts)]
    ((juxt :errors :options :arguments)
     (cli/parse-opts args (var-get opts) :in-order true))))

(defn -main [& [arg0 & args :as args*]]
  (try
    (loader/load-cli-opts!)
    (let [dotboot?           #(.endsWith (.getName (io/file %)) ".boot")
          script?            #(when (and % (.isFile (io/file %)) (dotboot? %)) %)
          [errs opts args**] (parse-boot-opts (if (script? arg0) args args*))
          args               (concat (if (script? arg0) [arg0] []) args**)]
      (when (seq errs)
        (binding [*out* *err*]
          (println (apply str (interpose "\n" errs)))
          (System/exit 1)))
      (when (:freshen opts) (reset! maven/update? :always))
      (when (:no-freshen opts) (reset! maven/update? :never))
      (reset! maven/offline? (:offline opts))
      (let [repl (loader/make-repl-server)
            out  (loader/make-io-server System/out)
            err  (loader/make-io-server System/err)
            pod  (loader/make-pod-client (loader/make-pod repl out err))]
        (pod `(do (require '[tailrecursion.boot])
                  (tailrecursion.boot/-main ~(boot-version) ~opts ~@args)))
        (Thread/sleep 50)
        (System/exit 0)))
    (catch Throwable e
      (binding [*out* *err*]
        (trace/print-cause-trace e)
        (flush)
        (System/exit 1)))))
