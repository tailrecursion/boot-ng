(ns tailrecursion.boot-classloader
  (:require
   [clojure.string                          :as string]
   [cemerick.pomegranate.aether             :as aether]
   [tailrecursion.boot-classloader.kahnsort :as kahn])
  (:import
   [org.springframework.util AntPathMatcher])
  (:gen-class))

(defn warn
  [& more]
  (binding [*out* *err*] (apply printf more) (flush)))

(defn transfer-listener
  [{type :type meth :method {name :name repo :repository} :resource err :error}]
  (when (.endsWith name ".jar")
    (case type
      :started              (warn "Retrieving %s from %s\n" name repo)
      (:corrupted :failed)  (when err (warn "Error: %s\n" (.getMessage err)))
      nil)))

(defn ^:from-leiningen build-url
  "Creates java.net.URL from string"
  [url]
  (try (java.net.URL. url)
       (catch java.net.MalformedURLException _
         (java.net.URL. (str "http://" url)))))

(defn ^:from-leiningen get-non-proxy-hosts
  []
  (let [system-no-proxy (System/getenv "no_proxy")]
    (if (not-empty system-no-proxy)
      (->> (string/split system-no-proxy #",")
           (map #(str "*" %))
           (string/join "|")))))

(defn ^:from-leiningen get-proxy-settings
  "Returns a map of the JVM proxy settings"
  ([] (get-proxy-settings "http_proxy"))
  ([key]
     (if-let [proxy (System/getenv key)]
       (let [url (build-url proxy)
             user-info (.getUserInfo url)
             [username password] (and user-info (.split user-info ":"))]
         {:host            (.getHost url)
          :port            (.getPort url)
          :username        username
          :password        password
          :non-proxy-hosts (get-non-proxy-hosts)}))))

(defn resolve-dependencies!*
  [env]
  (aether/resolve-dependencies
    :coordinates        (:dependencies env)
    :repositories       (when-let [repos (:repositories env)]
                          (->> repos
                            (map (juxt #((if (map? %) :url str) %) identity))
                            (into {})))
    :local-repo         (:local-repo   env)
    :offline?           (:offline?     env)
    :mirrors            (:mirrors      env)
    :proxy              (or (:proxy env) (get-proxy-settings))
    :transfer-listener  (or (:transfer-listener env) transfer-listener)))

(defn resolve-dependencies!
  [env]
  (->> (resolve-dependencies!* env)
    kahn/topo-sort
    (map (fn [x] {:dep x :jar (.getPath (:file (meta x)))}))))

(defn glob-match?
  [pattern path]
  (.match (AntPathMatcher.) pattern path))

(defn -main
  "I don't do a whole lot ... yet. (or ever.)"
  [& args]
  (println "Hello, World!"))
