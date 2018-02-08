(ns dvlopt.mqtt.v3

  ""

  {:author "Adam Helinski"}

  (:require [clojure.spec.alpha                :as s]
            [dvlopt.mqtt                       :as mqtt]
            [dvlopt.mqtt.v3.interop            :as mqtt.v3.interop]
            [dvlopt.mqtt.v3.interop.java       :as mqtt.v3.interop.java]
            [dvlopt.mqtt.v3.interop.clj        :as mqtt.v3.interop.clj]
            [dvlopt.mqtt.v3.interop.clj.tokens :as mqtt.v3.interop.clj.tokens]
            [dvlopt.mqtt.v3.stores             :as mqtt.v3.stores]
            [dvlopt.void                       :as void])
  (:import (java.util.concurrent ScheduledThreadPoolExecutor
                                 TimeUnit)
           (org.eclipse.paho.client.mqttv3 IMqttAsyncClient
                                           IMqttMessageListener
                                           MqttAsyncClient
                                           TimerPingSender)))




;;;;;;;;;; Declarations


(declare connected?)




;;;;;;;;;; Specs


(s/def ::client

  ::mqtt.v3.interop/IMqttAsyncClient)


(s/def ::connect

  (s/fspec :args (s/cat :delay (s/? ::mqtt/delay.msec.connection))
           :ret  nil?))


(s/def ::data.closed

  ::mqtt.v3.interop.clj.tokens/disconnection)


(s/def ::data.connection

  ::mqtt.v3.interop.clj.tokens/connection)


(s/def ::data.delivery

  ::mqtt.v3.interop.clj.tokens/delivery)


(s/def ::data.published

  ::mqtt.v3.interop.clj.tokens/publication)


(s/def ::data.subscribed

  ::mqtt.v3.interop.clj.tokens/subscription)


(s/def ::data.unsubscribed

  ::mqtt.v3.interop.clj.tokens/unsubscription)


(s/def ::error.connection

  (s/merge ::mqtt/error
           (s/keys :req [::connect])))


(s/def ::on-connection

  (s/fspec :args (s/or :success (s/cat :error nil?
                                       :data  ::data.connection)
                       :failure (s/cat :error ::error.connection
                                       :data  nil?))
           :ret  nil?))


(s/def ::on-closed

  (s/fspec :args (s/or :success (s/cat :error nil?
                                       :data  ::data.closed)
                       :failure (s/cat :error ::mqtt/error
                                       :data  nil?))))

(s/def ::on-connection-lost

  (s/fspec :args (s/cat :e ::error.connection)
           :ret  nil?))


(s/def ::on-message

  (s/fspec :args (s/cat :message ::mqtt/message)
           :ret  nil?))


(s/def ::on-message-delivered

  (s/fspec :args (s/cat :data ::data.delivery)
           :ret  nil?))


(s/def ::on-message-unhandled

  (s/fspec :args (s/cat :message ::mqtt/message)
           :ret  nil?))


(s/def ::on-published

  (s/fspec :args (s/or :success (s/cat :error nil?
                                       :data  ::data.published)
                       :failure (s/cat :error ::mqtt/error
                                       :data  nil?))
           :ret  nil?))


(s/def ::on-subscribed

  (s/fspec :args (s/or :success (s/cat :error nil?
                                       :data  ::data.subscribed)
                       :failure (s/cat :error ::mqtt/error
                                       :data  nil?))
           :ret  nil?))


(s/def ::on-unsubscribed

  (s/fspec :args (s/or :success (s/cat :error nil?
                                       :data  :data.unsubscribed)
                       :failure (s/cat :error ::mqtt/error
                                       :data  nil?))))


(s/def ::subscriptions

  (s/map-of ::mqtt/topic
            (s/keys :req [::on-message]
                    :opt [::mqtt/qos])))




;;;;;;;;;; API - Opening and closing a client


(s/fdef -fn-connect

  :args (s/cat :stpe          #(instance? ScheduledThreadPoolExecutor
                                          %)
               :client        ::client
               :mco           ::mqtt.v3.interop/MqttConnectOptions
               :on-connection ::on-connection)
  :ret  ::connect)


(defn- -fn-connect

  ""

  [^ScheduledThreadPoolExecutor stpe ^IMqttAsyncClient client mco on-connection]

  (fn connect

    ([]

     (let [map-exception (when on-connection
                           (fn map-exception [e]
                             (assoc (mqtt.v3.interop.clj/exception e)
                                    ::connect
                                    connect)))]
       (try
         (.connect client
                   mco
                   nil
                   (some-> on-connection
                           (mqtt.v3.interop.java/imqtt-action-listener map-exception
                                                                       mqtt.v3.interop.clj.tokens/connection)))
         (catch Throwable e
           (void/call on-connection
                      (map-exception e)
                      nil))))
     nil)


    ([^long delay-ms]

     (try
       (.schedule stpe
                  ^Runnable connect
                  delay-ms
                  TimeUnit/MILLISECONDS)
       (catch Throwable _
         nil))
     nil)))




(s/fdef open

  :args (s/cat :opts (s/? (s/keys :opt [::mqtt/clean-session?
                                        ::mqtt/client-id
                                        ::mqtt/interval.sec.keep-alive
                                        ::mqtt/manual-acks?
                                        ::mqtt/max-inflight
                                        ::mqtt/nodes
                                        ::mqtt/on-connection
                                        ::mqtt/on-connection-lost
                                        ::mqtt/on-message-delivered
                                        ::mqtt/on-message-unhandled
                                        ::mqtt/password
                                        ::mqtt/socket-factory
                                        ::mqtt/timeout.sec.connect
                                        ::mqtt/username
                                        ::mqtt/will
                                        ::mqtt.v3.stores/persisted?
                                        ::mqtt.v3.stores/size
                                        ::mqtt.v3.stores/store
                                        ::mqtt.v3.stores/when-full])))
  :ret  (s/keys :req [::client
                      ::mqtt/clean-session?
                      ::mqtt/client-id
                      ::mqtt/interval.sec.keep-alive
                      ::mqtt/manual-acks?
                      ::mqtt/timeout.sec.connect
                      ::mqtt/uris
                      ::mqtt.v3.stores/size]
                :opt [::mqtt/will.qos
                      ::mqtt/will.retained?
                      ::mqtt.v3.stores/persisted?
                      ::mqtt.v3.stores/when-full]))


(defn open

  ""

  (^IMqttAsyncClient

   []

   (open nil))


  (^IMqttAsyncClient

   [opts]

   (let [stpe         (ScheduledThreadPoolExecutor. 10)
         client-id    (or (::mqtt/client-id opts)
                          (format "dvlopt.mqtt-%d"
                                  (System/nanoTime)))
         client       (MqttAsyncClient. "tcp://localhost:1883"
                                        client-id
                                        (or (::mqtt.v3.stores/store opts)
                                            (mqtt.v3.stores/ram))
                                        (TimerPingSender.)
                                        stpe)
         opts-connect (mqtt.v3.interop.java/mqtt-connect-options opts)
         connect      (-fn-connect stpe
                                   client
                                   (::mqtt.v3.interop/MqttConnectOptions opts-connect)
                                   (::on-connection opts))
         return       (merge {::client            client
                              ::mqtt/client-id    client-id
                              ::mqtt/manual-acks? (let [manual-acks? (void/obtain ::mqtt/manual-acks?
                                                                                  opts
                                                                                  mqtt/defaults)]
                                                    (.setManualAcks client
                                                                    manual-acks?)
                                                    manual-acks?)}
                             (select-keys opts-connect
                                          [::mqtt/clean-session?
                                           ::mqtt/interval.sec.kee-alive
                                           ::mqtt/max-inflight
                                           ::mqtt/timeout.sec.connect
                                           ::mqtt/uris
                                           ::mqtt/will.payload
                                           ::mqtt/will.qos
                                           ::mqtt/will.retained?])
                             (let [result (mqtt.v3.interop.java/disconnected-buffer-options opts)]
                               (.setBufferOpts client
                                               (::mqtt.v3.interop/DisconnectedBufferOptions result))
                               (select-keys result
                                            [::mqtt.v3.stores/persisted?
                                             ::mqtt.v3.stores/size
                                             ::mqtt.v3.stores/when-full])))]
     (let [{on-connection-lost   ::on-connection-lost
            on-message-delivered ::on-message-delivered
            on-message-unhandled ::on-message-unhandled} opts]
       (when (or on-connection-lost
                 on-message-delivered
                 on-message-unhandled)
         (.setCallback client
                       (mqtt.v3.interop.java/mqtt-callback (when on-connection-lost
                                                             (fn on-connection-lost' [e]
                                                               (on-connection-lost (assoc e
                                                                                          ::connect
                                                                                          connect))))
                                                           on-message-delivered
                                                           on-message-unhandled))))
     (connect)
     return)))




(s/fdef close

  :args (s/cat :client ::client
               :opts   (s/? (s/keys :opt [::mqtt/timeout.msec.close
                                          ::on-closed])))
  :ret  nil?)


(defn close

  ""

  (^IMqttAsyncClient

   [client]

   (close client
          nil))


  (^IMqttAsyncClient

   [^IMqttAsyncClient client opts]
   
   (let [on-closed  (::on-closed opts)
         close      (fn close [data]
                      (future
                        (let [e (mqtt.v3.interop.clj/try-sync
                                  (.close client)
                                  nil)]
                          (void/call on-closed
                                     e
                                     (when-not e
                                       data)))))
         on-closed' (fn on-closed' [e data]
                      (if e
                        (void/call on-closed
                                   e
                                   nil)
                        (close data)))]
     (mqtt.v3.interop.clj/try-async
       on-closed
       (if (connected? client)
         (.disconnect client
                      (max 0
                           (void/obtain ::mqtt/timeout.msec.close
                                        opts
                                        mqtt/defaults))
                      (mqtt.v3.interop.java/imqtt-action-listener on-closed'
                                                                  mqtt.v3.interop.clj/exception
                                                                  mqtt.v3.interop.clj.tokens/disconnection))
         (close))))
     nil))




;;;;;;;;;; API - Misc


(s/fdef connected?

  :args (s/cat :client ::client)
  :ret  ::mqtt/connected?)


(defn connected?

  ""

  [^IMqttAsyncClient client]

  (.isConnected client))




(s/fdef current-uri

  :args (s/cat :client ::client)
  :ret  (s/nilable ::mqtt/uri))


(defn current-uri

  ""

  [^MqttAsyncClient client]

  (try
    (.getCurrentServerURI client)
    (catch Throwable _
      nil)))




(s/fdef client-id

  :args (s/cat :client ::client)
  :ret  ::mqtt/client-id)


(defn client-id

  ""

  [^IMqttAsyncClient client]

  (.getClientId client))




;;;;;;;;;; API - Publications


(s/fdef publish

  :args (s/cat :client ::client
               :topic  :mqtt/topic
               :opts   (s/? (s/keys :opt [::mqtt/payload
                                          ::mqtt/qos
                                          ::mqtt/retained?
                                          ::on-published])))
  :ret  ::client)



(defn publish

  ""

  ^IMqttAsyncClient

  [^IMqttAsyncClient client topic opts]

  (let [on-published (::on-published opts)]
    (mqtt.v3.interop.clj/try-async
      on-published
      (.publish client
                topic
                (void/obtain ::mqtt/payload
                             opts
                             mqtt/defaults)
                (void/obtain ::mqtt/qos
                             opts
                             mqtt/defaults)
                (void/obtain ::mqtt/retained?
                             opts
                             mqtt/defaults)
                nil
                (some-> on-published
                        (mqtt.v3.interop.java/imqtt-action-listener mqtt.v3.interop.clj/exception
                                                                    mqtt.v3.interop.clj.tokens/publication)))))
  client)




;;;;;;;;;; API - Subscriptions and message ack'ing


(defn- -subs-topics

  ""

  ^"[Ljava.lang.String;"

  [subscriptions]

  (into-array String
              (map first
                   subscriptions)))




(defn- -subs-qoses

  ""

  ^ints

  [subscriptions]

  (into-array Integer/TYPE
              (map (fn map-qoses [[_topic opts]]
                     (void/obtain ::mqtt/qos
                                  opts
                                  mqtt/defaults))
                   subscriptions)))



(defn- -subs-handlers

  ""

  ^"[Lorg.eclipse.paho.client.mqttv3.IMqttMessageListener;"

  [subscriptions]

  (into-array IMqttMessageListener
              (map (fn map-handlers [[topic opts]]
                     (if-let [handler (::on-message opts)]
                       (mqtt.v3.interop.java/imqtt-message-listener handler)
                       (throw (IllegalArgumentException. (format "Subscription handler missing for topic '%s'."
                                                                 topic)))))
                   subscriptions)))




(s/fdef subscribe

  ;; TODO spec subscriptions and handlers

  :args (s/cat :client        ::client
               :subscriptions ::subscriptions
               :opts          (s/? (s/keys :opt [::on-subscribed])))
  :ret  ::client)


(defn subscribe

  ""

  (^IMqttAsyncClient

   [client subscriptions]

   (subscribe client
              subscriptions
              nil))


  (^IMqttAsyncClient

   [^IMqttAsyncClient client subscriptions opts]

   (when-let [subscriptions' (seq subscriptions)]
     (let [on-subscribed (::on-subscribed opts)]
       (mqtt.v3.interop.clj/try-async
         on-subscribed
         (.subscribe client
                     (-subs-topics subscriptions')
                     (-subs-qoses subscriptions')
                     nil
                     (some-> on-subscribed
                             (mqtt.v3.interop.java/imqtt-action-listener mqtt.v3.interop.clj/exception
                                                                         mqtt.v3.interop.clj.tokens/subscription))
                     (-subs-handlers subscriptions')))))
   client))




(s/fdef unsubscribe

  :args (s/cat :client ::client
               :topics ::mqtt/topics
               :opts   (s/? (s/keys :opt [::on-unsubscribed])))
  :ret  ::client)


(defn unsubscribe

  ""

  (^IMqttAsyncClient

   [^IMqttAsyncClient client topics]

   (unsubscribe client
                topics
                nil))


  (^IMqttAsyncClient

   [^IMqttAsyncClient client topics opts]

   (let [on-unsubscribed (::on-unsubscribed opts)]
     (mqtt.v3.interop.clj/try-async
       on-unsubscribed
       (.unsubscribe client
                     (mqtt.v3.interop.java/string-array topics)
                     nil
                     (some-> on-unsubscribed
                             (mqtt.v3.interop.java/imqtt-action-listener mqtt.v3.interop.clj/exception
                                                                         mqtt.v3.interop.clj.tokens/unsubscription)))))
   client))




(s/fdef ack

  :args (s/cat :client  ::client
               :message (s/keys :req [::mqtt/message-id]
                                :opt [::mqtt/qos]))
  :ret  ::client)


(defn ack

  ""

  ;; TODO throws MqttException ?

  [^IMqttAsyncClient client message]

  (.messageArrivedComplete client
                           (::mqtt/message-id message)
                           (void/obtain ::mqtt/qos
                                        message
                                        mqtt/defaults))
  client)




;;;;;;;;;; API - Buffering of messages in transit


(s/fdef buffer-count

  :args (s/cat :client ::client)
  :ret  ::mqtt/int.pos)


(defn buffer-count

  ""

  [^MqttAsyncClient client]

  (.getBufferedMessageCount client))




(s/fdef buffer-get

  :args (s/cat :client ::client
               :index  ::mqtt/int.pos)
  :ret  (s/nilable ::mqtt/message.min))


(defn buffer-get

  ""

  [^MqttAsyncClient client index]

  (try
    (mqtt.v3.interop.clj/mqtt-message (.getBufferedMessage client
                                                           index))
    (catch IndexOutOfBoundsException _
      nil)))




;;;;;;;;;; Unsure, should this be part of the API ?


;; Actually does not remove messages from the underlying persistent store which is probably
;; the expected behaviour
#_(defn buffer-rm

  ""

  [^MqttAsyncClient client n]

  (try
    (.deleteBufferedMessage client
                            n)
    nil
    (catch IndexOutOfBoundsException _
      nil)))



;; Ditto.
#_(defn buffer-clear

  ""

  [^MqttAsyncClient client]

  (while (> (buffer-count client)
            0)
    (.deleteBufferedMessage client
                            0))
  nil)