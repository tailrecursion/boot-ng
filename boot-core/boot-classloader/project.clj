(defproject tailrecursion/boot-classloader "0.1.5"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure             "1.5.1"]
                 [org.clojure/tools.cli           "0.3.1"]
                 [org.springframework/spring-core "1.2.2"]
                 [com.cemerick/pomegranate        "0.2.0"]]
  :main ^:skip-aot tailrecursion.boot-classloader
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
