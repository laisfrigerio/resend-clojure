(ns resend.core
  "Public entry point for the resend-clojure SDK.

  Most users only need this namespace and the resource namespaces
  (e.g. resend.emails).

  Example:
    (require '[resend.core   :as resend])
    (require '[resend.emails :as emails])

    (def client (resend/create-client {:api-key (System/getenv \"RESEND_API_KEY\")}))

    (emails/send client
      {:from    \"Acme <no-reply@acme.com>\"
       :to      \"user@example.com\"
       :subject \"Hello\"
       :html    \"<strong>Hello!</strong>\"})"
  (:require [resend.internal.client :as client]))

(defn create-client
  "Creates and returns a ResendClient.

  Options map:
    :api-key   – required  – your Resend API key
    :base-url  – optional  – override API base URL (e.g. for testing)
    :adapter   – optional  – custom HttpAdapter (see resend.internal.http)

  Example:
    (create-client {:api-key \"re_xxxx\"})"
  ([]
   (create-client {}))
  ([opts]
   (let [api-key (or (:api-key opts)
                     (System/getenv "RESEND_API_KEY"))
         final-opts (assoc opts :api-key api-key)]
     (client/create-client final-opts))))