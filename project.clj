(defproject babe "1.0"
  :description "A data oriented static site generator."
  :url "https://github.com/askonomm/babe"
  :license {:name "MIT"
            :url  "https://raw.githubusercontent.com/askonomm/babe/master/LICENSE.txt"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [com.github.clj-easy/graal-build-time "0.1.3"]
                 [ch.qos.logback/logback-classic "1.2.6"]
                 [markdown-clj "1.10.6"]
                 [selmer "1.12.44"]
                 [http-kit "2.5.3"]]
  :plugins [[jonase/eastwood "0.9.9"]
            [lein-cloverage "1.2.2"]
            [lein-shell "0.5.0"]
            [lein-ancient "0.7.0"]]
  :aliases
  {"native"
   ["shell"
    "native-image" "--report-unsupported-elements-at-runtime" "--no-fallback"
    "--initialize-at-run-time=org.httpkit.client.ClientSslEngineFactory\\$SSLHolder"
    "-jar" "./target/babe.jar"]}
  :min-lein-version "2.0.0"
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
  :main babe.core
  :repl-options {:init-ns babe.core}
  :aot [babe.core]
  :uberjar-name "babe.jar")
