(ns muppeteer.core
  (:require [clojure.edn :as edn]
            [muppeteer.bot :refer [create-client start-main send-message set-bot-profile]]
            [clojure.core.async :refer [chan <! go-loop mult tap sliding-buffer]])
  (:import (discord4j.core GatewayDiscordClient)
           (discord4j.rest.util Image$Format)))

(defonce config (edn/read-string (slurp "config.edn")))

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