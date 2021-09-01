(ns muppeteer.bot
  (:require [clojure.java.io :as io]
            [clojure.core.async :refer [put!]])
  (:import (discord4j.core.event.domain.message MessageCreateEvent)
           (discord4j.core  GatewayDiscordClient DiscordClientBuilder)
           (discord4j.core.object.entity.channel TextChannel)
           (discord4j.rest.util Image)
           (discord4j.core.spec UserEditSpec)))

(defmacro as-consumer [f]
  `(reify java.util.function.Consumer
     (accept [this arg#]
       (~f arg#))))

(defn file->bytes [path]
  (with-open [in (io/input-stream path)
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn MessageCreateEvent->message [event]
  (let [message (.getMessage event)
        guild (.orElse (.getGuildId message) 0)
        channel (.getChannelId message)
        content (.getContent message)]
    {:type :message
     :data {:guild guild :channel channel :content content}}))

(defn build-message [spec content]
  (.. spec
      (setContent content)))

(defn send-message [^GatewayDiscordClient client channel-snowflake content]
  (let [channel (.block (.getChannelById client channel-snowflake))
        createMessageArg (if (instance? TextChannel channel)
                           content
                           (as-consumer (fn [spec] (build-message spec content))))]
    (.. channel (createMessage createMessageArg) (block))))

(defn create-client ^GatewayDiscordClient [token]
  (.. DiscordClientBuilder (create token) (build) (login) (block)))

(defn start-main [client msg-chan]
  (.. client
      (getEventDispatcher)
      (on MessageCreateEvent)
      (doOnError (as-consumer (fn [error] (println error))))
      (subscribe (as-consumer (fn [event] (put! msg-chan (MessageCreateEvent->message event)))))))

(defn set-bot-profile [^GatewayDiscordClient gateway image-path format nickname]
  (.. gateway (edit (as-consumer (fn [^UserEditSpec userEditSpec] (.. userEditSpec (setAvatar (Image/ofRaw (file->bytes image-path) format)) (setUsername nickname) (asRequest))))) (block)))

(defn set-bot-avatar [^GatewayDiscordClient gateway image-path format]
  (.. gateway (edit (as-consumer (fn [^UserEditSpec userEditSpec] (.. userEditSpec (setAvatar (Image/ofRaw (file->bytes image-path) format)) (asRequest))))) (block)))

(defn guild-by-id [client guild-id]
  (.block (.getGuildById client guild-id)))

(defn set-bot-nickname [client guild-id nickname]
  (let [guild (guild-by-id client guild-id)]
    (.. guild (changeSelfNickname nickname) (block))))
