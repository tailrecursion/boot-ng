(defproject tailrecursion/boot "2.0.0"
  :description  "A dependency setup/build tool for Clojure."
  :url          "https://github.com/tailrecursion/boot"
  :license      {:name  "Eclipse Public License"
                 :url   "http://www.eclipse.org/legal/epl-v10.html"}
  :aot          :all
  :main         tailrecursion.boot
  :profiles     {:uber {:dependencies
                        [[org.clojure/clojure            "1.5.1"]
                         [org.clojure/tools.cli          "0.3.1"]
                         [tailrecursion/boot-classloader "0.1.5"]]}
                 :dev  {:dependencies
                        [[org.clojure/clojure            "1.5.1"]
                         [org.clojure/tools.cli          "0.3.1"]
                         [tailrecursion/boot-classloader "0.1.5"]]}})
