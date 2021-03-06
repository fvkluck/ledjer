(defproject ledjer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[clojure.java-time "0.3.2"]
                 [environ "1.2.0"]
                 [funcool/lentes "1.3.3"]
                 [instaparse "1.4.10"]
                 [org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "1.0.206"]
                 [org.clojure/algo.generic "0.1.3"]]
  :plugins [[lein-environ "1.2.0"]]
  :repl-options {:init-ns ledjer.core}
  :main ledjer.core)
