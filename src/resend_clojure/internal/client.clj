(ns resend-clojure.internal.client
  "Constructs and holds the SDK client configuration.
 
  Following the pattern used by resend-node, resend-java, and other official
  SDKs, a single client record is created once (with the API key) and then
  threaded through every resource namespace call."
  (:require [resend-clojure.internal.http :as http]))

(def ^:private default-base-url "https://api.resend.com")

(defrecord ResendClient [api-key base-url adapter])

(defn create-client
  "Creates a new ResendClient.
 
  Options (map):
    :api-key   – required – Resend API key (string)
    :base-url  – optional – override the base URL (useful for testing proxies)
    :adapter   – optional – custom HttpAdapter implementation; defaults to hato
 
  Example:
    (create-client {:api-key \"re_xxxx\"})
    (create-client {:api-key \"re_xxxx\" :base-url \"http://localhost:8080\"})"
  [{:keys [api-key base-url adapter]}]
  {:pre [(string? api-key) (seq api-key)]}
  (map->ResendClient
   {:api-key  api-key
    :base-url (or base-url default-base-url)
    :adapter  (or adapter (http/default-adapter))}))