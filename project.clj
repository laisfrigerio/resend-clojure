(defproject net.clojars.laisfrigerio/resend-clojure "0.1.0-SNAPSHOT"
  :description "Clojure wrapper for the Resend Email API"
  :url "https://github.com/laisfrigerio/resend-clojure"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.11.3"]
                 [hato                "0.9.0"]
                 [cheshire            "5.13.0"]]
  :source-paths ["src"]
  :test-paths   ["test"]
  :profiles {:dev {:dependencies [[org.clojure/test.check "1.1.1"]]
                  :plugins [[lein-cljfmt "0.9.2"]
                            [com.github.clj-kondo/lein-clj-kondo "0.2.5"]]}}
  :aliases  {"lint"     ["clj-kondo" "--lint" "src" "test"]
             "lint-fix" ["cljfmt" "fix" "src" "test"]}
  :deploy-repositories [["clojars" {:url "https://repo.clojars.org"
                                    :sign-releases false}]])