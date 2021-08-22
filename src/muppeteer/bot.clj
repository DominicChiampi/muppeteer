(ns muppeteer.bot
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [>!!]])
  (:import (reactor.core.publisher Mono)
           (discord4j.core.event.domain.message MessageCreateEvent)
           (discord4j.core DiscordClient GatewayDiscordClient)
           (discord4j.rest.util Image Image$Format)
           (discord4j.core.spec UserEditSpec)))

(defmacro as-function [f & args]
  `(reify java.util.function.Function
     (apply [this arg#]
       (~f arg# ~@args))))

(defmacro as-runnable [f]
  `(reify java.lang.Runnable
     (run [this]
       (~f))))

(defmacro as-predicate [f]
  `(reify java.util.function.Predicate
     (test [this arg#]
       (~f arg#))))

(defmacro as-consumer [f]
  `(reify java.util.function.Consumer
     (accept [this arg#]
       (~f arg#))))

(defn file->bytes [path]
  (with-open [in (io/input-stream path)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn create-client [token]
  (. DiscordClient (create token)))

(defn message-filter [match]
  (as-predicate (fn [message] (if (.equals match (.getContent message)) java.lang.Boolean/TRUE java.lang.Boolean/FALSE))))

(defn gateway-edit-user [^GatewayDiscordClient gateway image-path format nickname]
  (.edit gateway (as-consumer (fn [^UserEditSpec userEditSpec] (.. userEditSpec (setAvatar (Image/ofRaw (file->bytes image-path) format)) (setUsername nickname) (asRequest))))))

(defn set-bot-profile [^DiscordClient client image-path format nickname]
  (.. client (withGateway (as-function gateway-edit-user image-path format nickname))))

(defn handle-message [message]
  (let [channelMono (.getChannel message)]
    (case (.getContent message)
      "!ping" (.. channelMono
                  (flatMap (as-function (fn [channel] (.createMessage channel "pong"))))
                  (doOnError (as-consumer (fn [error] (println error)))))
      "!texas" (do (.. channelMono
                       (doOnError (as-consumer (fn [error] (println error)))))
                   (.. channelMono
                       (flatMap (as-function (fn [channel] (set-bot-profile "./resources/texas.png" Image$Format/PNG "Senator McConaughey"))))))
      "!reset" (do (.. channelMono
                       (doOnError (as-consumer (fn [error] (println error)))))
                   (.. channelMono
                       (flatMap (as-function (fn [channel] (set-bot-profile "./resources/BunsenHoneydew.jpg" Image$Format/JPEG "TestMuppet"))))))
      (.. channelMono
          (flatMap (as-function (fn [channel] (Mono/just message))))))))

(defn dispatch-message []
  (as-function
   (fn [event]
     (.. Mono
         (just (.getMessage event))
         (flatMap (as-function handle-message))
         (doOnError (as-consumer (fn [error] (println error))))))))

(defn message-pump []
  (as-function
   (fn [gateway]
     (let [message (.on gateway MessageCreateEvent (dispatch-message))
           disconnect (.. gateway (onDisconnect) (doOnTerminate (as-runnable #(print "Disconnected!"))))]
       (Mono/when [message disconnect])))))

(defn attach-message-pump [client chan]
  (.. client (withGateway (message-pump)) (block))
  (>!! chan "terminate"))
