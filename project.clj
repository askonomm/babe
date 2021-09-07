(defproject babe "1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.json "2.4.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [markdown-clj "1.10.6"]
                 [selmer "1.12.44"]
                 [http-kit "2.5.3"]]
  :plugins [[jonase/eastwood "0.9.9"]
            [lein-cloverage "1.2.2"]
            [lein-shell "0.5.0"]]
  :aliases
  {"native"
   ["shell"
    "native-image" "--report-unsupported-elements-at-runtime"
    "--initialize-at-build-time=clojure,selmer,flatland.ordered,org.httpkit,markdown,complete,clj_yaml,babe" "--no-fallback"
    "--initialize-at-run-time=org.httpkit.client.ClientSslEngineFactory\\$SSLHolder"
    "-jar" "./target/babe.jar"]}
  :min-lein-version "2.0.0"
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"]
  :main babe.core
  :repl-options {:init-ns babe.core}
  :aot [babe.core]
  :uberjar-name "babe.jar")
;:profiles {:uberjar {:aot [babe.core]}})
