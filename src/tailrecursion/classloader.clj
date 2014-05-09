(ns tailrecursion.classloader
  (:require
   [server.socket       :as ss]
   [clojure.java.io     :as io]
   [classlojure.core    :as cl]
   [tailrecursion.maven :as maven])
  (:import
   [java.net Socket InetAddress URL URLClassLoader]
   [java.io File InputStreamReader OutputStreamWriter]
   [clojure.lang DynamicClassLoader LineNumberingPushbackReader]))

(def pods          (atom {}))
(def clojars       {:url "http://clojars.org/repo/"})
(def maven-central {:url "http://repo1.maven.org/maven2/"})

(def clj-dep
  (memoize #(maven/resolve-dependencies!
              {:repositories #{maven-central}
               :dependencies '[[org.clojure/clojure "1.5.1"]]})))

(def core-dep
  (memoize #(maven/resolve-dependencies!
              {:repositories #{clojars}
               :dependencies '[[tailrecursion/boot-core "2.0.0-SNAPSHOT"]]})))

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

(defn make-pod [port]
  (let [clj  (map :jar (clj-dep))
        core (map :jar (core-dep))
        cl   (apply make-classloader (concat clj core))
        port (cl/eval-in cl
               `(do (require '[tailrecursion.boot.core.classloader :as ~'pod-cl])
                    (reset! pod-cl/master-repl ~port)
                    (reset! pod-cl/repl-port (pod-cl/make-repl-server))))]
    (swap! pods assoc port cl)
    port))

(defn make-repl-server []
  (let [localhost (InetAddress/getLoopbackAddress)
        {:keys [server-socket]} (ss/create-repl-server 0 0 localhost)]
    (.getLocalPort server-socket)))

(def make-pod-client
  (memoize
    #(let [client (Socket. (InetAddress/getLoopbackAddress) %)
           writer (-> client (.getOutputStream) (OutputStreamWriter.))
           reader (-> client (.getInputStream) (InputStreamReader.) (LineNumberingPushbackReader.))]
       (fn [form] (binding [*out* writer] (read reader) (prn form) (read reader))))))
