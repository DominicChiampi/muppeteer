(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [muppeteer.bot :refer [create-client! start-main send-message set-bot-profile set-bot-nickname set-bot-avatar]]
            [clojure.core.async :refer [chan <! go-loop mult tap sliding-buffer]])
  (:import (discord4j.core GatewayDiscordClient)
           (discord4j.rest.util Image$Format)))

(defonce config (edn/read-string (slurp "config.edn")))
(defonce bot-pool (atom {}))

(defn sliding-buffered-channel []
  (chan (sliding-buffer (:chanbufflen config))))

(defn init-bot-pool! []
  (reset! bot-pool (reduce (fn [pool name] (assoc pool name {:client (create-client! (get-in config [:bot name :token])) :ready? true})) {} [:one :two :three])))

(defn handle-main-messages [client {:keys [guild channel content]}]
  (when (= content "!ping") (send-message client channel "!pong"))
  (when (= content "!texas") (set-bot-profile client "./resources/texas.png" Image$Format/PNG "Senator McConaughey")
        (send-message client channel "all right, all right, all right"))
  (when (= content "!science") (set-bot-profile client "./resources/BunsenHoneydew.jpg" Image$Format/JPEG "TestMuppet"))
  (when (= content "!yipyips")
    (set-bot-avatar client "./resources/yipyips.jpg" Image$Format/JPEG)
    (set-bot-nickname client guild "Yip Yips")
    (send-message client channel "Yip-yip-yip-yip... Uh-huh"))
  (when (= content "!test") (set-bot-nickname client guild "TestierMuppet")
        (send-message client channel "Science has left me a sad and lonely man")))

(defn handle-main-events [client event]
  (case (:type event)
    :message (handle-main-messages client (:data event))))

(defn -main [& args]
  (let [main-token (get-in config [:bot :main :token])
        ^GatewayDiscordClient main-client (create-client! main-token)
        events (sliding-buffered-channel)
        tap-mult (fn [] (tap (mult events) (sliding-buffered-channel)))]
    (init-bot-pool!)
    (go-loop [] (println (<! (tap-mult))) (recur))
    (go-loop [] (handle-main-events main-client (<! (tap-mult))) (recur))
    (start-main main-client events)
    (.. main-client (onDisconnect) (block))))