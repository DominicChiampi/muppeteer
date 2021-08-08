(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [muppeteer.bot :as bot]))

(def config (edn/read-string (slurp "config.edn")))

(defn -main [& args]
  (bot/start-bot! (:token config)))