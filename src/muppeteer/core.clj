(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojurewerkz.meltdown.streams :as ms :refer [create consume accept filter*]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [create-client listen! start-main]]
            [clojure.core.async :refer [chan <! go-loop mult tap]])
  (:import (reactor.core.publisher Mono)
           (discord4j.core.event.domain.message MessageCreateEvent)
           (discord4j.core DiscordClient GatewayDiscordClient DiscordClientBuilder)))

(defmacro as-function [f & args]
  `(reify java.util.function.Function
     (apply [this arg#]
       (~f arg# ~@args))))

(defonce config (edn/read-string (slurp "config.edn")))

(defonce state (atom {}))

(defn init-event-pump! [main-in])

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
  (let [main-token (get-in config [:main :token])
        ^GatewayDiscordClient main-client (create-client main-token)
        messages (chan 100)
        bus (mult messages)
        print-messages (chan)]
    (tap bus print-messages)
    (go-loop [] (println (<! print-messages)) (recur))
    (start-main main-client messages)
    (.. main-client (onDisconnect) (block))))