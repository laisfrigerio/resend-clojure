(ns resend-clojure.internal.request
  (:require [cheshire.core :as json]
            [resend-clojure.internal.http :as http]
            [resend-clojure.internal.response :as response]))

(defn- auth-headers
  "Returns the Authorization header map required by every Resend API call."
  [client]
  {"Authorization" (str "Bearer " (:api-key client))
   "Content-Type"  "application/json"})

(defn do-request
  "Executes an HTTP request using the given client and parameters.

  Parameters:
    :method  – :get | :post | :patch | :put | :delete
    :path    – string path to append to the base URL (e.g. \"/emails\")
    :body    – request body (string) or nil

  Returns a map with either {:data … :error nil} or {:data nil :error"
  [client method path body]
  (let [adapter (:adapter client)
        opts    (cond-> {:method  method
                         :url     (str (:base-url client) path)
                         :headers (auth-headers client)}
                  body (assoc :body (json/generate-string body)))]
    (-> (http/request adapter opts)
        response/handle-response)))