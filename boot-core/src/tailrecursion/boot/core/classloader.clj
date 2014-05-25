(ns tailrecursion.boot.core.classloader
  (:require
   [server.socket                       :as ss]
   [clojure.java.io                     :as io]
   [tailrecursion.boot.core.env         :as env]
   [tailrecursion.boot.core.util        :as util])
  (:import
   [clojure.lang LineNumberingPushbackReader]
   [java.io PrintWriter File InputStreamReader OutputStreamWriter]
   [java.net Socket InetAddress URLClassLoader URL URI]
   java.lang.management.ManagementFactory))

(def localhost    (InetAddress/getLoopbackAddress))
(def dep-jars     (atom []))
(def dependencies (atom (or (:dependencies (util/get-project 'tailrecursion/boot-core)) [])))
(def dfl-env      {:repositories #{"http://clojars.org/repo/" "http://repo1.maven.org/maven2/"}})
(def stdout-port  (atom nil))
(def stderr-port  (atom nil))
(def repl-port    (atom nil))
(def master-repl  (atom nil))

(def make-pod-client
  (memoize
    #(let [client (Socket. localhost %)
           writer (-> client (.getOutputStream) (OutputStreamWriter.))
           reader (-> client (.getInputStream) (InputStreamReader.) (LineNumberingPushbackReader.))]
       (fn [form] (binding [*out* writer] (read reader) (prn form) (read reader))))))

(defn make-repl-server []
  (-> (ss/create-repl-server 0 0 localhost) :server-socket .getLocalPort))

(defn make-io-client [port]
  (PrintWriter. (.getOutputStream (Socket. localhost port))))

(defn eval-in-master [form]
  ((make-pod-client @master-repl) form))

(def stdout #(make-io-client @stdout-port))
(def stderr #(make-io-client @stderr-port))

(defn prep-env [env]
  (-> (merge dfl-env env)
    (select-keys #{:dependencies :repositories :local-repo
                   :offline? :mirrors :proxy :transfer-listener})))

(defn exclude [syms coordinate]
  (if-let [idx (util/index-of coordinate :exclusions)]
    (let [exclusions (get coordinate (inc idx))]
      (assoc coordinate (inc idx) (into exclusions syms)))
    (into coordinate [:exclusions syms])))

(defn add-dirs! [dirs]
  (when (seq dirs)
    (eval-in-master
      `(do (require '[tailrecursion.classloader :as ~'cl-pod])
           (cl-pod/add-dirs! ~(deref repl-port) ~(vec dirs))))))

(defn resolve-deps! [env]
  (let [env (prep-env env)]
    (eval-in-master
      `(do (require '[tailrecursion.maven :as ~'mvn-pod])
           (mvn-pod/resolve-dependencies! '~env)))))

(defn add-deps! [env]
  (let [{deps :dependencies :as env} (prep-env env)
        loaded (->> @dependencies (map first) set)
        specs  (->> deps
                 (remove (comp (partial contains? loaded) first))
                 (mapv (partial exclude (vec loaded)))
                 (assoc env :dependencies)
                 resolve-deps!)]
    (add-dirs! (map :jar specs))
    (swap! dep-jars into (map :jar specs))
    (swap! dependencies into (map :dep specs))))

(defn glob-match? [pattern path]
  (eval-in-master
    `(do (require '[tailrecursion.boot :as ~'boot])
         (boot/glob-match? ~pattern ~path))))

(defn parse-opts [args argspecs options]
  (eval-in-master
    `(do (require '[tailrecursion.boot :as ~'boot])
         (boot/parse-opts ~(vec args) ~(vec argspecs) ~@options))))

(defn auto-flush [s]
  (eval-in-master
    `(do (.println System/out ~s) (.flush System/out))))

#_(defn make-pod [& {:keys [src-paths] :as env}]
  (let [env   (prep-env env)
        clj?  #(= 'org.clojure/clojure (first (:dep %)))
        {[{clj-jar :jar}] true, other-deps false}
        (->> env resolve-deps! (group-by clj?))]
    (when-not (and clj-jar (.exists (io/file clj-jar)))
      (throw (Exception. "Pod has no Clojure dependency")))
    (let [clj-path (.getPath (io/file clj-jar))
          pod      (cl/classlojure (str "file:" clj-path))
          deps     (->> other-deps (mapv :jar) (into src-paths))
          files    (->> deps (mapv io/file) (filter #(.exists %)))]
      (doseq [file files] (.addURL pod (.. file toURI toURL)))
      (fn [expr] (cl/eval-in pod expr)))))
