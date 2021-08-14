(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr :refer [create]]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [register-listeners]])
  (:import (discord4j.core DiscordClient)))

(def config (edn/read-string (slurp "config.edn")))

(defonce state (atom {:client {}}))

(defn init-reactor! []
  (let [r (create)]
    (swap! state assoc :reactor r)
    (mr/on r (match-all) (fn [event] (pprint event)))))

(defn notify [k message]
  (let [r (:reactor @state)]
    (mr/notify r k message)))

(defn on-exact [k f]
  (let [r (:reactor @state)]
    (mr/on r ($ k) f)))

(defn start-bot! [bot]
  (let [token (get-in config [bot :token])
        client (. DiscordClient (create token))]
    (swap! state assoc-in [:client bot] client)
    (.. client (withGateway (register-listeners)) (block))))

(defn -main [& args]
  (init-reactor!)
  (start-bot! :main))