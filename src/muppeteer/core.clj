(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [create-client attach-message-pump]]
            [clojure.core.async :refer [chan <!!]]))

(defonce config (edn/read-string (slurp "config.edn")))

(defonce state (atom {}))

(defn init-reactor! []
  (let [r (mr/create :dispatcher-type :thread-pool :event-routing-strategy :broadcast)]
    (swap! state assoc :reactor r)
    ;; (mr/on r (match-all) (fn [event] (pprint event)))
    ))

(defn notify [k message]
  (let [r (:reactor @state)]
    (mr/notify r k message)))

(defn on-exact [k f]
  (let [r (:reactor @state)]
    (mr/on r ($ k) f)))

(defn start-bot!
  ([bot token]
   (let [client (create-client token)
         terminate (chan)]
     (swap! state assoc bot {:client client :terminate-chan terminate})
     (attach-message-pump client terminate)))

  ([bot token terminate-chan]
   (let [client (create-client token)]
     (swap! state assoc bot {:client client :terminate-chan terminate-chan})
     (attach-message-pump client terminate-chan))))

(defn start-bot-sub [event]
  (let [bot (get-in event [:data :bot])
        token (get-in config [bot :token])
        terminate-chan (get-in event [:data :terminate-chan])]
    (if terminate-chan
      (start-bot! bot token terminate-chan)
      (start-bot! bot token))))

(defn init-subscribers []
  (let [r (:reactor @state)]
    (mr/on-error r Exception (fn [event]
                               (println event)))
    (mr/on r ($ :start-bot) (fn [event] (start-bot-sub event)))
    (mr/on r ($ :debug-state) (fn [event] (println @state)))))

(defn -main [& args]
  (let [main-terminate (chan)]
    (init-reactor!)
    (init-subscribers)
    (notify :start-bot {:bot :main :terminate-chan main-terminate})
    ;; (notify :debug-state {})
    (<!! main-terminate)))