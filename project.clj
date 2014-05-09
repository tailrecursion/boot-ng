(defproject tailrecursion/boot "2.0.0"
  :description  "A dependency setup/build tool for Clojure."
  :url          "https://github.com/tailrecursion/boot"
  :license      {:name  "Eclipse Public License"
                 :url   "http://www.eclipse.org/legal/epl-v10.html"}
  :aot          :all
  :main         tailrecursion.boot
  :profiles     {:uber {:dependencies
                        [[org.clojure/clojure             "1.5.1"]
                         [server-socket                   "1.0.0"]
                         [org.flatland/classlojure        "0.7.1"]
                         [org.tcrawley/dynapath           "0.2.3"]
                         [org.clojure/tools.cli           "0.3.1"]
                         [org.springframework/spring-core "1.2.2"]
                         [com.cemerick/pomegranate        "0.2.0"]]}
                 :dev  {:dependencies
                        [[org.clojure/clojure             "1.5.1"]
                         [server-socket                   "1.0.0"]
                         [org.flatland/classlojure        "0.7.1"]
                         [org.tcrawley/dynapath           "0.2.3"]
                         [org.clojure/tools.cli           "0.3.1"]
                         [org.springframework/spring-core "1.2.2"]
                         [com.cemerick/pomegranate        "0.2.0"]]}})
