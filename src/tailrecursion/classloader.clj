(ns tailrecursion.classloader
  (:require
   [server.socket       :as ss]
   [clojure.java.io     :as io]
   [classlojure.core    :as cl]
   [tailrecursion.maven :as maven])
  (:import
   [java.util.jar JarFile]
   [java.net Socket InetAddress URL URLClassLoader]
   [java.io File PrintWriter InputStreamReader OutputStreamWriter]
   [clojure.lang DynamicClassLoader LineNumberingPushbackReader]))

(def clj-version   "1.5.1")
(def core-version  "2.0.0-SNAPSHOT")
(def localhost     (InetAddress/getLoopbackAddress))
(def clojars       {:url "http://clojars.org/repo/"})
(def maven-central {:url "http://repo1.maven.org/maven2/"})
(def pods          (atom {}))

(def clj-dep
  (memoize
    #(maven/resolve-dependencies!
       {:repositories #{maven-central}
        :dependencies [['org.clojure/clojure clj-version]]})))

(defn initial-core-dep [& [offline?]]
  (maven/resolve-dependencies!
    {:repositories #{clojars}
     :offline?     offline?
     :dependencies [['tailrecursion/boot-core core-version]]}))

(def core-dep (memoize initial-core-dep))

(defn slurp-entry [jar-path entry-path]
  (let [jar (JarFile. jar-path)]
    (when-let [entry (.getEntry jar entry-path)]
      (-> jar (.getInputStream entry) slurp))))

(defn load-cli-opts! []
  (let [dep (try (initial-core-dep true)
                 (catch Throwable _ (initial-core-dep false)))
        jar (-> (initial-core-dep) first :jar)
        cli "tailrecursion/boot/core/cli_opts.clj"]
    (when-let [src (slurp-entry jar cli)]
      (-> (File/createTempFile "cli_opts" ".clj")
        (doto .deleteOnExit)
        (as-> x (.getPath x) (doto x (spit src)) (load-file x))))))

(defn add-url! [classloader url]
  (.addURL classloader (io/as-url (io/file url))))

(defn add-urls! [classloader urls]
  (doseq [u urls] (add-url! classloader u)))

(defn add-deps! [port env]
  (add-urls! (@pods port) (map :jar (maven/resolve-dependencies! env))))

(defn add-dirs! [port dirs]
  (add-urls! (@pods port) dirs))

(defn dyn-classloader [urls ext]
  (doto (DynamicClassLoader. ext) (add-urls! urls)))
  
(defn make-classloader [& urls]
  (doto ^URLClassLoader (dyn-classloader urls cl/ext-classloader)
        (.loadClass "clojure.lang.RT")
        (cl/eval-in* '(require 'clojure.main))))

(defn make-pod [port stdout stderr]
  (let [clj  (map :jar (clj-dep))
        core (map :jar (core-dep))
        cl   (apply make-classloader (concat clj core))
        port (cl/eval-in cl
               `(do (require '[tailrecursion.boot.core.classloader :as ~'pod-cl])
                    (reset! pod-cl/master-repl ~port)
                    (reset! pod-cl/stdout-port ~stdout)
                    (reset! pod-cl/stderr-port ~stderr)
                    (reset! pod-cl/repl-port (pod-cl/make-repl-server))))]
    (swap! pods assoc port cl)
    port))

(defn make-repl-server []
  (-> (ss/create-repl-server 0 0 localhost) :server-socket .getLocalPort))

(defn io-pipe [writer]
  (fn [in out]
    (loop [b (.read in)]
      (when (pos? b)
        (.write writer b)
        (recur (.read in))))))

(defn make-io-server [writer]
  (-> (ss/create-server 0 (io-pipe writer) 50 localhost) :server-socket .getLocalPort))

(def make-pod-client
  (memoize
    #(let [client (Socket. localhost %)
           writer (-> client (.getOutputStream) (OutputStreamWriter.))
           reader (-> client (.getInputStream) (InputStreamReader.) (LineNumberingPushbackReader.))]
       (fn [form] (binding [*out* writer] (read reader) (prn form) (read reader))))))
