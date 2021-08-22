(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojurewerkz.meltdown.streams :as ms :refer [create consume accept filter*]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [create-client listen!]]
            [clojure.core.async :refer [chan <!!]]))

(defonce config (edn/read-string (slurp "config.edn")))

(defonce state (atom {}))

(defn init-reactor! []
  (let [r (mr/create :dispatcher-type :thread-pool :event-routing-strategy :broadcast)
        m (create)]
    (swap! state assoc :reactor r)
    (swap! state assoc :messages m)
    ;; (mr/on r (match-all) (fn [event] (pprint event)))
    ))

(defn notify [k message]
  (let [r (:reactor @state)]
    (mr/notify r k message)))

(defn on-exact [k f]
  (let [r (:reactor @state)]
    (mr/on r ($ k) f)))

(defn main-listen [message]
  (println message)
  (notify :message message))

(defn start-bot!
  ([bot token listen]
   (let [client (create-client token)
         terminate (chan)]
     (swap! state assoc bot {:client client :terminate-chan terminate})
     (listen! client terminate listen)))

  ([bot token listen terminate-chan]
   (let [client (create-client token)]
     (swap! state assoc bot {:client client :terminate-chan terminate-chan})
     (listen! client terminate-chan listen))))

(defn start-bot-sub [event]
  (let [bot (get-in event [:data :bot])
        token (get-in config [bot :token])
        terminate-chan (get-in event [:data :terminate-chan])
        listen (or (get-in event [:data :listen]) (fn []))]
    (if terminate-chan
      (start-bot! bot token listen terminate-chan)
      (start-bot! bot token listen))))

(defn init-subscribers []
  (let [r (:reactor @state)]
    (mr/on-error r Exception (fn [event]
                               (println event)))
    (mr/on r ($ :start-bot) (fn [event] (start-bot-sub event)))
    (mr/on r ($ :debug-state) (fn [event] (println @state)))
    (mr/on r ($ :message) (fn [message] (println message)))))

(defn -main [& args]
  (let [main-terminate (chan)]
    (init-reactor!)
    (init-subscribers)
    (notify :start-bot {:bot :main :terminate-chan main-terminate :listen main-listen})
    ;; (notify :debug-state {})
    (<!! main-terminate)))