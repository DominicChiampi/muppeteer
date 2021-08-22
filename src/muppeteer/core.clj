(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [create-client attach-message-pump]]))

(defonce config (edn/read-string (slurp "config.edn")))

(defonce state (atom {:client {}}))

(defn init-reactor! []
  (let [r (mr/create :dispatcher-type :thread-pool :event-routing-strategy :broadcast)]
    (swap! state assoc :reactor r)
    (mr/on r (match-all) (fn [event] (pprint event)))))

(defn notify [k message]
  (let [r (:reactor @state)]
    (mr/notify r k message)))

(defn on-exact [k f]
  (let [r (:reactor @state)]
    (mr/on r ($ k) f)))

(defn start-bot! [bot token]
  (let [client (create-client token)]
    (println token)
    (swap! state assoc-in [:client bot] client)
    (attach-message-pump client)))

(defn init-subscribers []
  (let [r (:reactor @state)
        main-token (get-in config [:main :token])]
    (mr/on-error r Exception (fn [event]
                               (println event)))
    (mr/on r ($ :start-bot) (fn [event] (start-bot! event main-token)))
    (mr/on r ($ :debug-state) (fn [event] (println (:client @state))))))

(defn -main [& args]
  (init-reactor!)
  (init-subscribers)
  (notify :start-bot :main)
  (notify :debug-state {}))