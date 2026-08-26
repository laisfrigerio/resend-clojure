(ns resend.internal.http
  "Defines the IHttpAdapter protocol and the default hato-based implementation.
 
  Separating transport from business logic allows consumers to inject a
  MockAdapter in tests or swap in any alternative HTTP client without
  touching SDK namespaces."
  (:require [hato.client :as hato]
            [cheshire.core :as json]))

(defprotocol IHttpAdapter
  "Thin abstraction over HTTP so the SDK is not coupled to a specific client."
  (request [this opts]
    "Executes an HTTP request described by `opts` map.
 
    Expected keys in `opts`:
      :method  – :get | :post | :patch | :put | :delete
      :url     – full URL string
      :headers – map of string->string headers
      :body    – serialised request body (string) or nil
 
    Must return a map with at least:
      :status  – HTTP status code (integer)
      :body    – parsed response body (map/vector)"))

(defrecord HatoAdapter []
  IHttpAdapter
  (request [_this {:keys [method url headers body]}]
    (let [req  (cond-> {:method           method
                        :url              url
                        :headers          headers
                        :content-type     :json
                        :as               :string
                        :throw-exceptions false}
                 body (assoc :body body))
          resp (hato/request req)]
      {:status (:status resp)
       :body   (when-let [b (:body resp)]
                 (when (seq b)
                   (json/parse-string b true)))})))

(defn default-adapter
  "Returns a new HatoAdapter instance.
  Consumers can replace this with their own record implementing IHttpAdapter."
  []
  (->HatoAdapter))