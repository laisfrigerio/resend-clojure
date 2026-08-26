(ns resend-clojure.core
  "Public entry point for the resend-clojure SDK.

  Most users only need this namespace and the resource namespaces
  (e.g. resend-clojure.emails).

  Example:
    (require '[resend-clojure.core   :as resend])
    (require '[resend-clojure.emails :as emails])

    (def client (resend/create-client {:api-key (System/getenv \"RESEND_API_KEY\")}))

    (emails/send client
      {:from    \"Acme <no-reply@acme.com>\"
       :to      \"user@example.com\"
       :subject \"Hello\"
       :html    \"<strong>Hello!</strong>\"})"
  (:require [resend-clojure.internal.client :as client]))

(defn create-client
  "Creates and returns a ResendClient.

  Options map:
    :api-key   – required  – your Resend API key
    :base-url  – optional  – override API base URL (e.g. for testing)
    :adapter   – optional  – custom HttpAdapter (see resend-clojure.internal.http)

  Example:
    (create-client {:api-key \"re_xxxx\"})"
  [opts]
  (client/create-client opts))