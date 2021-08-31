(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [clojurewerkz.meltdown.reactor :as mr]
            [clojurewerkz.meltdown.selectors :refer [match-all $]]
            [clojurewerkz.meltdown.streams :as ms :refer [create consume accept filter*]]
            [clojure.pprint :refer [pprint]]
            [muppeteer.bot :refer [create-client listen! start-main send-message set-bot-profile]]
            [clojure.core.async :refer [chan <! go-loop mult tap sliding-buffer]])
  (:import (reactor.core.publisher Mono)
           (discord4j.core.event.domain.message MessageCreateEvent)
           (discord4j.core DiscordClient GatewayDiscordClient DiscordClientBuilder)
           (discord4j.rest.util Image Image$Format)))

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

(defn sliding-buffered-channel [buff]
  (chan (sliding-buffer buff)))

(defn handle-main-messages [client {:keys [guild channel content]}]
  (when (= content "!ping") (send-message client channel "!pong"))
  (when (= content "!texas") (set-bot-profile client "./resources/texas.png" Image$Format/PNG "Senator McConaughey")
        (send-message client channel "all right, all right, all right"))
  (when (= content "!science") (set-bot-profile client "./resources/BunsenHoneydew.jpg" Image$Format/JPEG "TestMuppet"))
  (when (= content "!yipyips") (set-bot-profile client "./resources/yipyips.jpg" Image$Format/JPEG "Yip Yips")
        (send-message client channel "Yip-yip-yip-yip... Uh-huh")))

(defn handle-main-events [client event]
  (case (:type event)
    :message (handle-main-messages client (:data event))))

(defn -main [& args]
  (let [main-token (get-in config [:main :token])
        ^GatewayDiscordClient main-client (create-client main-token)
        events (sliding-buffered-channel 100)
        bus (mult events)]
    (go-loop [] (println (<! (tap bus (sliding-buffered-channel 100)))) (recur))
    (go-loop [] (handle-main-events main-client (<! (tap bus (sliding-buffered-channel 100)))) (recur))
    (start-main main-client events)
    (.. main-client (onDisconnect) (block))))